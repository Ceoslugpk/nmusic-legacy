# SimpMusic Legacy (Android 4.3+)

This is a separate, reduced edition for older Android devices. It browses audio already stored on the device and provides song search, play/pause, previous/next, seeking, audio-focus handling, and background playback with notification controls.

It does not include YouTube Music streaming, account sign-in, synced lyrics, downloads, recommendations, equalizer, Cast, or the modern Compose interface. Those parts of the current app depend on Android API 26+, Java 21, and current AndroidX/Media3 libraries; this edition uses the Android framework APIs available on API 18 instead.

## Build

Install JDK 17 or newer and Android SDK Platform 35 / Build Tools 35.0.0, set `ANDROID_HOME` or `ANDROID_SDK_ROOT`, then run from this directory:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Its package is `com.maxrave.simpmusic.legacy`, separate from the modern SimpMusic app.
