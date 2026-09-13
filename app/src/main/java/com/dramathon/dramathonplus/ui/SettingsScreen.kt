package com.dramathon.dramathonplus.ui

import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.tv.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.text.HtmlCompat
import com.dramathon.dramathonplus.BuildConfig
import com.dramathon.dramathonplus.DeviceIdManager
import com.dramathon.dramathonplus.DramaViewModel
import com.dramathon.dramathonplus.ui.theme.Accent
import com.dramathon.dramathonplus.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: DramaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subscriptionExpiry by viewModel.subscriptionExpiry.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    
    val deviceId = remember(context) { DeviceIdManager.getStableId(context) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<JSONObject?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var noUpdateFound by remember { mutableStateOf(false) }
    
    var showCodeEntryDialog by remember { mutableStateOf(false) }

    val currentVersion = BuildConfig.VERSION_NAME

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            border = Border(BorderStroke(1.dp, Color.Gray))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Subscription Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Accent
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (subscriptionExpiry != null) {
                    Text("Expires on: $subscriptionExpiry", color = MaterialTheme.colorScheme.onSurface)
                } else if (isUnlocked) {
                    Text("Premium Unlocked", color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text("Free Plan", color = TextSecondary)
                }
                
                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCodeEntryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(containerColor = Accent),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Text("Enter Unlock Code", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            border = Border(BorderStroke(1.dp, Color.Gray))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Accent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Version: $currentVersion", color = MaterialTheme.colorScheme.onSurface)
                Text("Developed by DramaThon Plus Team", color = TextSecondary, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            checkingUpdate = true
                            noUpdateFound = false
                            val latest = viewModel.checkUpdate()
                            checkingUpdate = false
                            if (latest != null) {
                                val latestTag = latest.optString("tag_name").removePrefix("v")
                                if (latestTag != currentVersion) {
                                    updateInfo = latest
                                    showUpdateDialog = true
                                } else {
                                    noUpdateFound = true
                                }
                            } else {
                                noUpdateFound = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(containerColor = Accent),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    enabled = !checkingUpdate
                ) {
                    if (checkingUpdate) {
                        CustomProgressIndicator(size = 32.dp)
                    } else {
                        Text("Check for Update", fontWeight = FontWeight.Bold)
                    }
                }

                if (noUpdateFound) {
                    Text(
                        "You are on the latest version.",
                        color = Color.Green,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showCodeEntryDialog) {
        CodeEntryDialog(
            onDismiss = { showCodeEntryDialog = false },
            onUnlock = { code, callback ->
                viewModel.unlock(code, deviceId, context, callback)
            }
        )
    }

    if (showUpdateDialog && updateInfo != null) {
        val rawBody = updateInfo?.optString("body") ?: "No release notes provided."
        val textColorArgb = TextSecondary.toArgb()

        Dialog(onDismissRequest = { showUpdateDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(500.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "A new version (${updateInfo?.optString("tag_name") ?: ""}) is available.",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Native TextView to render HTML content safely
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    movementMethod = LinkMovementMethod.getInstance()
                                    setTextColor(textColorArgb)
                                    textSize = 14f
                                }
                            },
                            update = { textView ->
                                textView.text = HtmlCompat.fromHtml(
                                    rawBody.replace("\n", "<br>"),
                                    HtmlCompat.FROM_HTML_MODE_COMPACT
                                )
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Primary Action: Direct Download
                        Button(
                            onClick = {
                                showUpdateDialog = false
                                val assets = updateInfo?.optJSONArray("assets")
                                var downloadUrl = updateInfo?.optString("html_url") ?: ""

                                if (assets != null) {
                                    for (i in 0 until assets.length()) {
                                        val asset = assets.getJSONObject(i)
                                        val name = asset.optString("name").lowercase()
                                        if (name.endsWith(".apk")) {
                                            downloadUrl = asset.optString("browser_download_url")
                                            break
                                        }
                                    }
                                }

                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(containerColor = Accent),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Text("Download APK", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showUpdateDialog = false },
                            colors = ButtonDefaults.colors(containerColor = Color.Transparent, contentColor = TextSecondary)
                        ) {
                            Text("Later", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
