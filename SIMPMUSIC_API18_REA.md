# SimpMusic Android 4.3 reverse-engineering notes

## Result

The 38 KB `SimpMusic-API18-debug.apk` in this repository is a standalone local-file player. It is not a downleveled build of the full SimpMusic application. REA analysis of the published SimpMusic 2.3.0 FOSS APK confirms that Android 4.3 support requires a separate compatibility implementation: the shipped app declares API 26 as its minimum, its entry activity hosts Compose UI, and playback is built around a Media3 library session. The SimpMusic source tree also marks the app and shared Android modules as API 26 minimum.

This report is static analysis of one APK plus comparison with the checked-out source. It does not claim runtime behavior was observed on a device.

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

Keep Android 4.3 support as a separate app target. Do not describe the current 38 KB local player APK as the full SimpMusic app. A credible reduced streaming edition needs at least a compatible UI, source/extractor path, playback engine, queue/state handling, and background-media controls. The current Compose UI and Media3 service cannot simply be switched on for API 18; build a legacy UI and service layer, then reuse only shared Kotlin/domain/network code that passes an API 18 and dependency compatibility audit.

A useful implementation order is:

1. Identify the SimpMusic repository revision and the exact streaming path used by the FOSS build (search result to extractor result to playable stream URI).
2. Port that one path into the API 18 app while keeping the legacy `MediaPlayer` service, queue, and notification behavior explicit.
3. Add search/source selection and error handling, then validate against a real Android 4.3 device or emulator.
4. Expand to library, lyrics, downloads, and account-dependent features only after their dependencies and service APIs are separately shown to work on API 18.

This is a substantially reduced compatibility edition, not a promise that every current SimpMusic feature will run on API 18.
