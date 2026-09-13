package com.dramathon.dramathonplus.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.dramathon.dramathonplus.PlaybackService
import com.dramathon.dramathonplus.decrypt.VideoManager
import com.dramathon.dramathonplus.ui.theme.Accent
import com.dramathon.dramathonplus.ui.theme.Bg2
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import org.json.JSONObject

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoPlayer(
    episode: JSONObject,
    episodes: List<JSONObject>,
    watchedEpisodes: Set<String> = emptySet(),
    bookId: Long = 0L,
    isUnlocked: Boolean = false,
    isInPipMode: Boolean = false,
    onClose: () -> Unit,
    onNextEpisode: (JSONObject) -> Unit,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showControls by remember { mutableStateOf(true) }
    var showEpisodeList by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isEpisodeLoading by remember { mutableStateOf(false) }

    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    val currentEpisodeState = rememberUpdatedState(episode)
    val episodesState = rememberUpdatedState(episodes)
    val isUnlockedState = rememberUpdatedState(isUnlocked)
    val onNextEpisodeState = rememberUpdatedState(onNextEpisode)

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                
                controller.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            duration = controller.contentDuration.coerceAtLeast(0)
                        }
                        if (state == Player.STATE_ENDED) {
                            val currentEpisodes = episodesState.value
                            val currentEp = currentEpisodeState.value
                            
                            val currentIndex = currentEpisodes.indexOfFirst { 
                                it.optInt("chapterIndex") == currentEp.optInt("chapterIndex") 
                            }
                            
                            if (!isUnlockedState.value && currentEp.optInt("chapterIndex") == 6) {
                                showUnlockDialog = true
                                isPlaying = false
                            } else if (currentIndex != -1 && currentIndex < currentEpisodes.size - 1) {
                                onNextEpisodeState.value(currentEpisodes[currentIndex + 1])
                            }
                        }
                    }
                    
                    override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                        isPlaying = isPlayingParam
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || 
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            val currentIndex = controller.currentMediaItemIndex
                            val currentEpisodes = episodesState.value
                            if (currentIndex >= 0 && currentIndex < currentEpisodes.size) {
                                val newEp = currentEpisodes[currentIndex]
                                if (newEp.optInt("chapterIndex") != currentEpisodeState.value.optInt("chapterIndex")) {
                                    onNextEpisodeState.value(newEp)
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            mediaController?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }

    LaunchedEffect(episodes, mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        val items = episodes.map { ep ->
            val metadata = MediaMetadata.Builder()
                .setTitle(ep.optString("chapterName").ifEmpty { "Episode ${ep.optInt("chapterIndex") + 1}" })
                .setArtist("DramaThon Plus")
                .build()
            MediaItem.Builder()
                .setMediaId(ep.optLong("chapterId").toString())
                .setMediaMetadata(metadata)
                .setUri("https://dramathonplus.com/ep_${ep.optInt("chapterIndex")}")
                .build()
        }
        controller.setMediaItems(items)
        val index = episodes.indexOfFirst { it.optInt("chapterIndex") == episode.optInt("chapterIndex") }
        if (index != -1) {
            controller.seekToDefaultPosition(index)
        }
    }

    LaunchedEffect(episode, mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        val expectedIndex = episodes.indexOfFirst { it.optInt("chapterIndex") == episode.optInt("chapterIndex") }
        
        if (expectedIndex != -1 && controller.currentMediaItemIndex != expectedIndex) {
            controller.seekToDefaultPosition(expectedIndex)
        }

        val videoUrl = episode.optString("videoPath")
        if (videoUrl.isNotEmpty()) {
            isEpisodeLoading = true
            // Pause current while loading next to keep in sync
            controller.pause()
            
            val file = VideoManager.prepareDecryptedVideo(videoUrl, context)
            if (file != null) {
                val currentItem = controller.getMediaItemAt(expectedIndex)
                val newItem = currentItem.buildUpon()
                    .setUri(file.toUri())
                    .build()
                
                controller.replaceMediaItem(expectedIndex, newItem)
                if (controller.currentMediaItemIndex == expectedIndex) {
                    controller.prepare()
                    controller.play()
                }
            }
            isEpisodeLoading = false
        }
    }

    LaunchedEffect(isPlaying, mediaController, isEpisodeLoading) {
        val controller = mediaController ?: return@LaunchedEffect
        if (isPlaying && !isEpisodeLoading) {
            while (true) {
                currentPosition = controller.currentPosition
                delay(500)
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && !showEpisodeList) {
            playPauseFocusRequester.requestFocus()
            delay(3000)
            showControls = false
        }
    }

    BackHandler(enabled = !isInPipMode) {
        if (showEpisodeList) {
            showEpisodeList = false
        } else if (showControls) {
            showControls = false
        } else {
            mediaController?.stop()
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            VideoManager.clearCache(context)
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
                    showControls = true
                    true
                } else if (showControls) {
                    // Reset timeout on any key press when controls are shown
                    showControls = false
                    showControls = true
                    false
                } else {
                    false
                }
            }
            .clickable(enabled = !isInPipMode) { 
                if (!showEpisodeList) {
                    showControls = !showControls 
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    player = mediaController
                    useController = false
                    keepScreenOn = true
                }
            },
            update = {
                it.player = mediaController
            }
        )

        if (isEpisodeLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CustomProgressIndicator()
            }
        }
        
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Center Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    val currentIndex = episodes.indexOfFirst { 
                        it.optInt("chapterIndex") == episode.optInt("chapterIndex") 
                    }
                    
                    IconButton(
                        onClick = {
                            mediaController?.seekToPreviousMediaItem()
                            showControls = true
                        },
                        enabled = currentIndex > 0,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Text("⏮", color = if (currentIndex > 0) Color.White else Color.Gray, fontSize = 48.sp)
                    }

                    IconButton(
                        onClick = {
                            mediaController?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                            showControls = true // Reset timeout
                        },
                        modifier = Modifier
                            .size(96.dp)
                            .focusRequester(playPauseFocusRequester)
                            .onKeyEvent {
                                if (it.key == Key.DirectionCenter || it.key == Key.Enter) {
                                    mediaController?.let { p ->
                                        if (p.isPlaying) p.pause() else p.play()
                                    }
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            },
                        colors = IconButtonDefaults.colors(containerColor = Accent.copy(alpha = 0.8f))
                    ) {
                        Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 48.sp)
                    }

                    IconButton(
                        onClick = {
                            mediaController?.seekToNextMediaItem()
                            showControls = true
                        },
                        enabled = currentIndex < episodes.size - 1,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Text("⏭", color = if (currentIndex < episodes.size - 1) Color.White else Color.Gray, fontSize = 48.sp)
                    }
                }

                // Bottom Seekbar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 48.dp, start = 48.dp, end = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, fontSize = 16.sp)
                        Text(formatTime(duration), color = Color.White, fontSize = 16.sp)
                    }
                    
                    // Standard Slider for now as TV Material 3 might not have a dedicated TV Slider yet
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            currentPosition = it.toLong()
                            mediaController?.seekTo(it.toLong())
                            showControls = true // Reset timeout
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent {
                                if (it.key == Key.DirectionLeft) {
                                    val newPos = (currentPosition - 10000).coerceAtLeast(0)
                                    currentPosition = newPos
                                    mediaController?.seekTo(newPos)
                                    showControls = true
                                    true
                                } else if (it.key == Key.DirectionRight) {
                                    val newPos = (currentPosition + 10000).coerceAtMost(duration)
                                    currentPosition = newPos
                                    mediaController?.seekTo(newPos)
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }

        if (showEpisodeList) {
            Surface(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Bg2)
            ) {
                Column(modifier = Modifier.padding(32.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Episodes",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showEpisodeList = false }) {
                            Text("✕", color = Color.White, fontSize = 24.sp)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(episodes) { ep ->
                            val chapIndex = ep.optInt("chapterIndex")
                            val isSelected = chapIndex == episode.optInt("chapterIndex")
                            val isWatched = watchedEpisodes.contains("${bookId}_$chapIndex")
                            
                            val bgColor = when {
                                isSelected -> Accent
                                isWatched -> Color(0xFFFF4081)
                                else -> Color.White.copy(alpha = 0.1f)
                            }

                            Surface(
                                onClick = {
                                    mediaController?.seekToDefaultPosition(chapIndex)
                                    showEpisodeList = false
                                },
                                modifier = Modifier.aspectRatio(1f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = bgColor,
                                    focusedContainerColor = Accent.copy(alpha = 0.8f)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (chapIndex + 1).toString(),
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    
                                    if (isWatched && !isSelected) {
                                        Text(
                                            text = "👁", 
                                            fontSize = 12.sp, 
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showUnlockDialog) {
            CodeEntryDialog(
                onDismiss = { 
                    showUnlockDialog = false
                    onClose()
                },
                onUnlock = { code, callback ->
                    onUnlock(code) { success, msg ->
                        if (success) {
                            showUnlockDialog = false
                            mediaController?.play()
                        }
                        callback(success, msg)
                    }
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
