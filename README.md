# X Download — Android 1.0 alpha
Arabic-first link downloader without an internal browser. Android 10+; ARM32/ARM64. yt-dlp and FFmpeg for supported media; Android DownloadManager for direct file URLs. Media jobs require the application to stay open. No DRM support. Download only authorized content.

## Build
JDK 17, Android SDK 36, Gradle 8.13. Run gradle :app:assembleDebug. Output app/build/outputs/apk/debug/app-debug.apk. Initial build and device tests pending.

## Dependencies and credits
- https://github.com/yausername/youtubedl-android (0.18.1)
- https://github.com/yt-dlp/yt-dlp
- https://ffmpeg.org/
Review and comply with upstream licenses and corresponding-source obligations before distributing binaries. This is a personal test build, not a production release.
