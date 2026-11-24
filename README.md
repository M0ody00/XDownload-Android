# X Download — Android 1.1 Lite
Arabic-first link downloader without an internal browser. Android 10+; ARM64 only. yt-dlp and FFmpeg for supported media; Android DownloadManager for direct file URLs. Media jobs require the application to stay open. No DRM support. Download only authorized content.

## Build
JDK 17, Android SDK 36, Gradle 8.13. Run gradle :app:assembleDebug. Output app/build/outputs/apk/debug/app-debug.apk. Lite build uses a separate application ID to avoid signature conflicts with the previous test APK. Signature, archive integrity and ABI are checked by CI; physical device testing is pending. Debug builds use runner-local test signing; future updates may need uninstall/reinstall.

## Dependencies and credits
- https://github.com/yausername/youtubedl-android (0.18.1)
- https://github.com/yt-dlp/yt-dlp
- https://ffmpeg.org/
Review and comply with upstream licenses and corresponding-source obligations before distributing binaries. This is a personal test build, not a production release.
