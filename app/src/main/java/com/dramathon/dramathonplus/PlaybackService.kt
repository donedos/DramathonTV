package com.dramathon.dramathonplus

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

    private fun prepareEpisode(mediaItem: MediaItem, index: Int, exoPlayer: ExoPlayer) {
        val extras = mediaItem.mediaMetadata.extras ?: Bundle.EMPTY
        val realUrl = extras.getString("real_url")
        val isUnlocked = extras.getBoolean("is_unlocked", false)
        val chapterIndex = extras.getInt("chapter_index", 0)

        // Enforce the free-user episode limit (max 7 episodes) at the service level.
        if (!isUnlocked && chapterIndex >= 7) {
            if (index == exoPlayer.currentMediaItemIndex) {
                exoPlayer.pause()
            }
            return
        }

        if (realUrl != null && !preparationJobs.containsKey(mediaItem.mediaId)) {
            preparationJobs[mediaItem.mediaId] = serviceScope.launch {
                try {
                    val file = VideoManager.prepareDecryptedVideo(realUrl, this@PlaybackService)
                    if (file != null) {
                        // Ensure we update the correct item in the timeline
                        val currentTimeline = exoPlayer.currentTimeline
                        for (i in 0 until currentTimeline.windowCount) {
                            if (exoPlayer.getMediaItemAt(i).mediaId == mediaItem.mediaId) {
                                val newItem = mediaItem.buildUpon()
                                    .setUri(file.toUri())
                                    .build()
                                exoPlayer.replaceMediaItem(i, newItem)
                                
                                // If this was the current item and we are waiting for it, prepare/play
                                if (i == exoPlayer.currentMediaItemIndex) {
                                    Log.d("PlaybackService", "Replacing current item, state: ${exoPlayer.playbackState}")
                                    if (exoPlayer.playbackState == Player.STATE_BUFFERING || 
                                        exoPlayer.playbackState == Player.STATE_IDLE ||
                                        !exoPlayer.playWhenReady) {
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    }
                                }
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackService", "Failed to prepare episode", e)
                } finally {
                    preparationJobs.remove(mediaItem.mediaId)
                }
            }
        }
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
                if (uri.startsWith("https://dramathonplus.com/ep_")) {
                    Log.d("PlaybackService", "Expected error for dummy URI: ${error.message}. Waiting for decryption.")
                } else {
                    Log.e("PlaybackService", "Playback error", error)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && exoPlayer.playWhenReady) {
                    // Proactively prepare the next episode when the current one starts playing
                    val nextIndex = exoPlayer.currentMediaItemIndex + 1
                    if (nextIndex < exoPlayer.mediaItemCount) {
                        val nextItem = exoPlayer.getMediaItemAt(nextIndex)
                        val nextUri = nextItem.localConfiguration?.uri ?: Uri.EMPTY
                        if (nextUri.toString().startsWith("https://dramathonplus.com/ep_")) {
                            prepareEpisode(nextItem, nextIndex, exoPlayer)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return

                val uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
                Log.d("PlaybackService", "Transition to $uri, reason: $reason")
                if (uri.toString().startsWith("https://dramathonplus.com/ep_")) {
                    prepareEpisode(mediaItem, exoPlayer.currentMediaItemIndex, exoPlayer)
                }
            }
        })

        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            @UnstableApi
            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAll(exoPlayer.availableCommands)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .build()
            }

            @UnstableApi
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_CHANGE_MEDIA_ITEMS,
                    Player.COMMAND_GET_TIMELINE -> true
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
}
