package com.dramathon.dramathonplus

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Surface
import coil.ImageLoader
import com.dramathon.dramathonplus.ui.MainScreen
import com.dramathon.dramathonplus.ui.theme.DramaThonPlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var viewModel: DramaViewModel? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkNotificationPermission()
        
        val imageLoader = ImageLoader.Builder(this)
            .crossfade(true)
            .build()

        setContent {
            val vm: DramaViewModel = viewModel()
            viewModel = vm
            
            LaunchedEffect(intent) {
                intent.getStringExtra("tab")?.let { tab ->
                    vm.loadTab(tab)
                }
            }

            val isDarkMode by vm.isDarkMode.collectAsState()

            CompositionLocalProvider(coil.compose.LocalImageLoader provides imageLoader) {
                DramaThonPlusTheme(darkTheme = isDarkMode) {
                    Surface {
                        MainScreen(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("tab")?.let { tab ->
            lifecycleScope.launch {
                viewModel?.loadTab(tab)
            }
        }
    }

    override fun onUserLeaveHint() {
        if (viewModel?.selectedDrama?.value != null && viewModel?.playingEpisode?.value != null) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(1, 1))
                    .build()
            )
        }
        super.onUserLeaveHint()
    }
    
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel?.setPipMode(isInPictureInPictureMode)
        
        if (!isInPictureInPictureMode && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED).not()) {
            viewModel?.setPlayingEpisode(null)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
