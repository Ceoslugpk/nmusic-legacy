# SimpMusic Android 4.3 reverse-engineering notes

## Result

The first 38 KB `SimpMusic-API18-debug.apk` was only a local-file player. The updated 4.4 MB API 18 build now adds YouTube search, AAC/M4A stream playback, and downloads to `Music/SimpMusic`, while keeping a separate legacy UI and MediaPlayer service. It is still a reduced compatibility edition, not the full modern SimpMusic application: the current app requires API 26, Compose, and Media3.

REA findings below are static analysis of one APK plus comparison with the checked-out source. The new API 18 APK was compiled and its package/minimum SDK/permissions were checked from its manifest. It has not been run on a physical API 18 device; the environment also blocks direct requests to YouTube, so live search and extraction could not be smoke-tested here.

## Artifact and analysis tools

- Artifact: official SimpMusic 2.3.0 FOSS universal APK, package `com.maxrave.simpmusic`, version code 60.
- APK SHA-256: `f1656fee14ff7c0715e8e6a584c95f85f6ae9662f4bf9ce022df2696296b66cb`.
- REA CLI: 6.5.0.
- Android decompiler: `jadx-headless-mcp` 0.7.1; SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`.
- Source comparison checkout: SimpMusic `a81563e5b598b1b66db5b40491a183bbc0c5d741`; `core` submodule `aa9025...`. This identifies the local source examined; it is not proof that the APK was built from that exact commit.
- The APK was analyzed statically; REA did not execute or install it. Signature validation, native-library coverage, runtime behavior, and reflective/dynamically loaded call paths were not verified.

## Findings

1. **The APK is explicitly blocked from installation on Android 4.3.** REA read `minSdk=26`, `targetSdk=36`, and compile SDK 37 from the manifest. It found 17,262 classes and 3,123 resources. Evidence: `ev_8ce054dfdb711183ab8acc4e9cab10195168a5902bc4f2d69eb1b3b5f56a60b2`.

2. **The main activity is a Compose host, not a legacy View screen.** REA decompiled `com.maxrave.simpmusic.MainActivity.onCreate`; the method wires app state and then installs a Compose content view. Source at the inspected SimpMusic revision uses `androidx.activity.compose.setContent`. Recreating only the manifest with `minSdk=18` would not replace this UI stack. Evidence: `ev_c6254f5dc284582c260b8613c5840ecdf0750b7cb73d26a4e8aafc1c9f692a18`.

3. **Playback is a service/session subsystem.** The manifest registers `com.maxrave.media3.service.SimpleMediaService` for Media3 session/library and Android media-browser actions, and also registers a download service and media-button receiver. REA's class inventory shows the service lifecycle methods and a binder. Its decompiled `onCreate` sets a Media3 notification provider, creates the library session around the injected player, installs notification updates, and creates a controller. `onStartCommand` logs the incoming action and returns the sticky restart value `1`. Evidence: class `ev_399d82188cfa4c3bee9fae4badec2b252dc2eba1a812724e6f45a431985ca106`, `onCreate` `ev_63d8dd1faa2f72e01b50826b69ade9ecb8e9195c53230ab5b993f2ceae7b2022`, and `onStartCommand` `ev_a9d560a55f379ed5eaa7a32de36676365512dba9bf8348031b4ca7986fb43e86`.

4. **The activity is statically connected to the playback service.** REA found 12 incoming class-reference nodes for `SimpleMediaService`, including `MainActivity`; the class inventory coverage was complete for JADX's using-node inventory. This is a static relationship, not a runtime call trace. Evidence: `ev_1ead8cbed681dd9d400bd7f992405d83f851c4bc4b4071f9a7b4cc09823d353e`.

5. **The API 26 floor is present throughout the Android source modules.** In the checked-out `SimpMusic` revision, `androidApp`, `composeApp`, `core/media/media3`, `core/domain`, `core/data`, and `core/common` all declare `minSdk = 26`. Lowering only the application manifest is therefore insufficient. It would require a dependency/API compatibility pass across shared modules, with replacements or feature gates wherever their libraries or calls require newer Android APIs.

## Porting implications

The API 18 edition now uses a legacy View UI and Android framework `MediaPlayer` service, while NewPipe Extractor supplies YouTube search and signature-decoded audio stream URLs. The download action resolves an AAC/M4A stream and writes it to public Music storage. This follows the same broad search-to-stream-to-download flow traced in SimpMusic, with API 18-compatible components.

The source trace found a YouTube Music search request in `Ytmusic.kt`, stream URL selection in `StreamRepositoryImpl`, and Media3-managed downloads in `DownloadUtils.kt`. APK class-name searches for those repositories returned no matches, consistent with R8 obfuscation; REA could inspect the activity and playback service, but did not recover those implementation classes from the APK.

Current API 18 scope is local library search/playback plus YouTube video/song search, background playback controls, and audio file downloads. Playlist/account synchronization, lyrics, recommendations, equalizer, Cast, the modern Compose interface, and complete feature parity remain unported. The next meaningful validation is an install and end-to-end search/play/download test on a real API 18 device; that hardware check is still outstanding.
