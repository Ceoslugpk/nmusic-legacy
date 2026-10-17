# SimpMusic Legacy (Android 4.3+)

This API 18 edition now includes online YouTube search, playback, and saving audio for offline listening, alongside music already stored on the device. Search results are fetched on demand; selecting a result resolves its audio stream and starts the existing background player. **Save** stores the extracted audio in `Music/SimpMusic` and adds it to Android's media scanner.

This is a reduced compatibility client based on REA inspection of SimpMusic v2.3.0 and its shared YouTube Music/extractor flow. It is not the full modern SimpMusic feature set: account sync, recommendations, lyrics, playlists/queue management, casts, equalizer, modern Compose UI, etc. are not ported yet. YouTube's undocumented endpoints and stream formats can change, and playback/download availability depends on region, age restrictions, and YouTube service changes. On Android 4.3, the OS's TLS certificates/codecs can also limit connections or supported formats.

YouTube Music search calls the WEB_REMIX endpoint and parses the current song renderer fields, with the NewPipe web search as a fallback. NewPipe Extractor v0.26.1 also resolves audio streams; it is GPL-3.0-or-later, with source under `third_party/NewPipeExtractor`. NewPipe's nanojson fork is included under `third_party/nanojson` (Apache-2.0). Remaining runtime libraries are resolved from Maven Central in `app/build.gradle`.

## Build

Install JDK 17 or newer and Android SDK Platform 35 / Build Tools 35.0.0, set `ANDROID_HOME` or `ANDROID_SDK_ROOT`, then run from this directory:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk` and checked into this folder as [`SimpMusic-API18-debug.apk`](SimpMusic-API18-debug.apk). Its package is `com.maxrave.simpmusic.legacy`, separate from the modern app. The app targets API 28 so storage downloads use the public Music directory on API 18 devices. The search-response parser has local fixture tests. Live network calls and end-to-end playback/download have not yet been exercised on a physical Android 4.3 device.
