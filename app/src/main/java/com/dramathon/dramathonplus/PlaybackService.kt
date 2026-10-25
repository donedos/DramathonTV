package com.dramathon.dramathonplus

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dramathon.dramathonplus.decrypt.VideoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val preparationJobs = mutableMapOf<String, Job>()
    private val failedUrls = mutableMapOf<String, MutableMap<String, Long>>()
    private var stallJob: Job? = null
    private var lastReportedFailure: Pair<String, String>? = null

    private fun prepareEpisode(mediaItem: MediaItem, index: Int, exoPlayer: ExoPlayer) {
        val extras = mediaItem.mediaMetadata.extras ?: Bundle.EMPTY
        val realUrl = extras.getString("real_url")
        val isUnlocked = extras.getBoolean("is_unlocked", false)
        val isLocked = extras.getBoolean("is_locked", false)
        val chapterIndex = extras.getInt("chapter_index", 0)

        // Enforce the free-user episode limit (max 7 episodes) at the service level.
        if (!isUnlocked && chapterIndex >= 7) {
            if (index == exoPlayer.currentMediaItemIndex) {
                exoPlayer.pause()
            }
            reportFailure(mediaItem, "This episode is part of the premium pack.")
            return
        }

        if (isLocked && !isUnlocked) {
            reportFailure(mediaItem, "This episode is locked. Unlock to watch it.")
            return
        }

        val urls = candidateUrls(extras)
        if (urls.isEmpty()) {
            // The stream url is not resolved yet (it is fetched when the episode is
            // picked), so there is nothing to report here.
            Log.d("PlaybackService", "No stream url yet for episode ${mediaItem.mediaId}")
            return
        }

        if (!preparationJobs.containsKey(mediaItem.mediaId)) {
            preparationJobs[mediaItem.mediaId] = serviceScope.launch {
                try {
                    attemptPreparation(mediaItem, exoPlayer, urls)
                } catch (e: Exception) {
                    Log.e("PlaybackService", "Failed to prepare episode", e)
                    reportFailure(mediaItem, "This episode could not be loaded. Please try again.")
                } finally {
                    preparationJobs.remove(mediaItem.mediaId)
                }
            }
        }
    }

    /**
     * Tries every rendition of the episode until one can be played. A single broken
     * quality or CDN must not leave the episode stuck on a loading screen.
     */
    private suspend fun attemptPreparation(
        mediaItem: MediaItem,
        exoPlayer: ExoPlayer,
        urls: List<String>
    ) {
        for (url in urls) {
            // A URL that just failed is not retried right away, otherwise every
            // playlist rebuild would hammer the CDN with the same broken request.
            if (isRecentlyFailed(mediaItem.mediaId, url)) continue
            val preparedUri = try {
                if (isDirectStream(url)) {
                    // HLS/DASH playlists are streamed by ExoPlayer directly,
                    // downloading them would only save the playlist text.
                    Log.d("PlaybackService", "Streaming playlist detected, playing directly: $url")
                    url.toUri()
                } else {
                    VideoManager.prepareDecryptedVideo(url, this@PlaybackService)?.toUri()
                }
            } catch (e: Exception) {
                Log.w("PlaybackService", "Rendition failed, trying the next one: $url", e)
                null
            }
            if (preparedUri == null) {
                recordFailure(mediaItem.mediaId, url)
                continue
            }

            // Ensure we update the correct item in the timeline
            val currentTimeline = exoPlayer.currentTimeline
            for (i in 0 until currentTimeline.windowCount) {
                if (exoPlayer.getMediaItemAt(i).mediaId == mediaItem.mediaId) {
                    val newItem = mediaItem.buildUpon()
                        .setUri(preparedUri)
                        .build()
                    exoPlayer.replaceMediaItem(i, newItem)

                    // If this was the current item and we are waiting for it, prepare/play
                    if (i == exoPlayer.currentMediaItemIndex) {
                        Log.d("PlaybackService", "Replacing current item, state: ${exoPlayer.playbackState}")
                        if (exoPlayer.playbackState == Player.STATE_BUFFERING ||
                            exoPlayer.playbackState == Player.STATE_IDLE ||
                            !exoPlayer.playWhenReady
                        ) {
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
                    break
                }
            }
            clearFailure()
            failedUrls.remove(mediaItem.mediaId)
            return
        }
        reportFailure(mediaItem, "This episode could not be loaded. Please try again.")
    }

    private fun recordFailure(mediaId: String, url: String) {
        failedUrls.getOrPut(mediaId) { mutableMapOf() }[url] = SystemClock.elapsedRealtime()
    }
    private fun isRecentlyFailed(mediaId: String, url: String): Boolean {
        val timestamp = failedUrls[mediaId]?.get(url) ?: return false
        return SystemClock.elapsedRealtime() - timestamp < RETRY_WINDOW_MS
    }

    /**
     * Watches a stream that keeps buffering. Without it a broken segment leaves the
     * player on an endless spinner, so the next rendition is tried instead.
     */
    private fun startStallWatchdog(exoPlayer: ExoPlayer) {
        stallJob?.cancel()
        stallJob = serviceScope.launch {
            delay(STALL_TIMEOUT_MS)
            if (exoPlayer.playbackState != Player.STATE_BUFFERING) return@launch
            val mediaItem = exoPlayer.currentMediaItem ?: return@launch
            val uri = mediaItem.localConfiguration?.uri?.toString() ?: ""
            Log.w("PlaybackService", "Stalled while buffering for ${STALL_TIMEOUT_MS}ms: $uri")
            recordFailure(mediaItem.mediaId, uri)
            val urls = candidateUrls(mediaItem.mediaMetadata.extras ?: Bundle.EMPTY)
            val next = urls.firstOrNull { !isRecentlyFailed(mediaItem.mediaId, it) }
            if (next != null && !preparationJobs.containsKey(mediaItem.mediaId)) {
                Log.w("PlaybackService", "Stalled, retrying with another rendition: $next")
                preparationJobs[mediaItem.mediaId] = serviceScope.launch {
                    try {
                        attemptPreparation(mediaItem, exoPlayer, urls)
                    } finally {
                        preparationJobs.remove(mediaItem.mediaId)
                    }
                }
            } else {
                reportFailure(mediaItem, "This episode is taking too long to load. Please try again.")
            }
        }
    }

    private fun candidateUrls(extras: Bundle): List<String> {
        val urls = LinkedHashSet<String>()
        extras.getString("real_url")?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
        extras.getString("real_urls")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { urls.add(it) }
        return urls.toList()
    }

    private fun reportFailure(mediaItem: MediaItem, message: String) {
        if (lastReportedFailure == mediaItem.mediaId to message) return
        lastReportedFailure = mediaItem.mediaId to message
        Log.w("PlaybackService", "Episode ${mediaItem.mediaId} failed: $message")
        mediaSession?.setSessionExtras(Bundle().apply {
            putString(EXTRA_ERROR, message)
            putString(EXTRA_ERROR_ITEM, mediaItem.mediaId)
        })
    }

    private fun clearFailure() {
        lastReportedFailure = null
        mediaSession?.setSessionExtras(Bundle.EMPTY)
    }

    private fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd")
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val mediaItem = exoPlayer.currentMediaItem
                val uri = mediaItem?.localConfiguration?.uri?.toString() ?: ""
                if (uri.startsWith(DUMMY_PREFIX)) {
                    Log.d("PlaybackService", "Expected error for dummy URI: ${error.message}. Waiting for decryption.")
                    return
                }
                Log.e("PlaybackService", "Playback error", error)
                if (mediaItem == null) return
                // The stream itself failed: retry with another rendition before giving up.
                val extras = mediaItem.mediaMetadata.extras ?: Bundle.EMPTY
                val urls = candidateUrls(extras)
                recordFailure(mediaItem.mediaId, uri)
                val next = urls.firstOrNull { !isRecentlyFailed(mediaItem.mediaId, it) }
                if (next != null && !preparationJobs.containsKey(mediaItem.mediaId)) {
                    Log.w("PlaybackService", "Retrying episode with another rendition: $next")
                    preparationJobs[mediaItem.mediaId] = serviceScope.launch {
                        try {
                            attemptPreparation(mediaItem, exoPlayer, urls)
                        } finally {
                            preparationJobs.remove(mediaItem.mediaId)
                        }
                    }
                } else {
                    reportFailure(mediaItem, "This episode could not be played.")
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING) {
                    startStallWatchdog(exoPlayer)
                } else {
                    stallJob?.cancel()
                    stallJob = null
                }
                if (state == Player.STATE_READY && exoPlayer.playWhenReady) {
                    // Proactively prepare the next episode when the current one starts playing
                    val nextIndex = exoPlayer.currentMediaItemIndex + 1
                    if (nextIndex < exoPlayer.mediaItemCount) {
                        val nextItem = exoPlayer.getMediaItemAt(nextIndex)
                        val nextUri = nextItem.localConfiguration?.uri ?: Uri.EMPTY
                        if (nextUri.toString().startsWith(DUMMY_PREFIX)) {
                            prepareEpisode(nextItem, nextIndex, exoPlayer)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return

                val uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
                Log.d("PlaybackService", "Transition to $uri, reason: $reason")
                if (uri.toString().startsWith(DUMMY_PREFIX)) {
                    prepareEpisode(mediaItem, exoPlayer.currentMediaItemIndex, exoPlayer)
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)
                // The playlist is rebuilt whenever an episode gains its stream url, which
                // happens after the item was already selected, so pick it up here.
                val index = exoPlayer.currentMediaItemIndex
                if (index == C.INDEX_UNSET) return
                val item = exoPlayer.getMediaItemAt(index)
                val uri = item.localConfiguration?.uri?.toString() ?: ""
                if (uri.startsWith(DUMMY_PREFIX)) {
                    prepareEpisode(item, index, exoPlayer)
                }
            }
        })

        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            @UnstableApi
            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAll(exoPlayer.availableCommands)
                    .add(COMMAND_PLAY_PAUSE)
                    .add(COMMAND_STOP)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_CHANGE_MEDIA_ITEMS)
                    .add(COMMAND_GET_TIMELINE)
                    .build()
            }

            @UnstableApi
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_PLAY_PAUSE,
                    COMMAND_STOP,
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_CHANGE_MEDIA_ITEMS,
                    COMMAND_GET_TIMELINE -> true
                    else -> super.isCommandAvailable(command)
                }
            }
        }
            
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                // Skips will be handled by the controller in the UI via listeners
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ERROR = "playback_error"
        const val EXTRA_ERROR_ITEM = "playback_error_item"
        const val DUMMY_PREFIX = "https://dramathonplus.com/ep_"
        private const val RETRY_WINDOW_MS = 10_000L
        private const val STALL_TIMEOUT_MS = 25_000L
    }
}
