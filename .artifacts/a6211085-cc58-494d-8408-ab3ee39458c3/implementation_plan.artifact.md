# Implement VLC Player as a replacement for ExoPlayer

This plan outlines the migration from `androidx.media3:media3-exoplayer` to `org.videolan.android:libvlc-all`. We will maintain the `Media3` session architecture by creating a VLC-backed implementation of the `androidx.media3.Player` interface.

## User Review Required

> [!IMPORTANT]
> **Architecture Choice**: To avoid refactoring the entire UI (`VideoPlayerView`) and Service (`PlaybackService`), we will implement a `VlcPlayer` wrapper that adheres to the `androidx.media3.Player` interface. This keeps the `MediaController` and `MediaSession` logic intact.

> [!WARNING]
> **VLC UI Component**: The `androidx.media3.ui.PlayerView` is highly coupled with `ExoPlayer`. We will replace it with a custom `AndroidView` that manages a `SurfaceView` specifically for LibVLC.

## Proposed Changes

### Dependencies

#### [MODIFY] [libs.versions.toml](file:///Users/zed/AndroidStudioProjects/DramaThonTV/gradle/libs.versions.toml)
- Add `libvlc` version `3.7.5`.
- Add `libvlc-all` library definition.

#### [MODIFY] [build.gradle.kts](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/build.gradle.kts)
- Add `libvlc-all` dependency.
- Keep `media3-session` and `media3-common`.
- Optionally remove `media3-exoplayer` (though keeping it for now might ease the transition).

---

### Core Implementation

#### [NEW] [VlcPlayer.kt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/java/com/dramathon/dramathonplus/vlc/VlcPlayer.kt)
- Create a class implementing `androidx.media3.Player`.
- This class will wrap `org.videolan.libvlc.MediaPlayer`.
- Implement essential methods: `play`, `pause`, `stop`, `release`, `seekTo`, `setMediaItem`, etc.
- Handle `Player.Listener` callbacks by bridging VLC events.

#### [MODIFY] [PlaybackService.kt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/java/com/dramathon/dramathonplus/PlaybackService.kt)
- Replace `ExoPlayer` initialization with `VlcPlayer`.
- Remove ExoPlayer-specific `DefaultRenderersFactory` and `MediaCodecSelector` logic, as VLC handles its own decoders.

---

### UI Layer

#### [MODIFY] [VideoPlayerView.kt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/java/com/dramathon/dramathonplus/ui/VideoPlayerView.kt)
- Replace `androidx.media3.ui.PlayerView` in the `AndroidView` factory.
- Use a `SurfaceView` and attach it to the `VlcPlayer` instance.
- Ensure `VlcPlayer` can receive the `Surface` or `VideoLayout`.

---

### Resources

#### [NEW] [vlc_player_view.xml](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/res/layout/vlc_player_view.xml)
- A simple layout containing a `SurfaceView` for VLC rendering, replacing `player_view.xml` (which uses `PlayerView`).

## Verification Plan

### Automated Tests
- N/A (UI and Service intensive).

### Manual Verification
1.  **Basic Playback**: Ensure videos start, pause, and stop correctly.
2.  **Seeking**: Verify the seekbar updates and seeking works.
3.  **Episode Switching**: Test "Next/Previous" functionality.
4.  **PIP Mode**: Verify Picture-in-Picture still works with VLC.
5.  **Codec Check**: Verify that the green/grey distortion issues previously seen with ExoPlayer are resolved by VLC's internal decoders.
