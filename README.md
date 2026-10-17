# nmusic Legacy

Independent Android music application targeting Android 4.3 (API 18) and newer.

This repository currently provides a debug APK for physical-device testing. Android runtime behavior and audio output have not yet been verified on a physical device.

See the [v0.1.0 release](https://github.com/Ceoslugpk/nmusic-legacy/releases/tag/v0.1.0) for the APK.

## APK status

- Package: `org.nmusic.legacy`
- Minimum SDK: 18
- Target SDK: 28
- Provider proof: Wikimedia Commons metadata and permitted media-range request tested headlessly
- Physical-device testing: pending


## SimpMusic reduced legacy edition

A separate reduced compatibility edition built for Android 4.3 (API 18) and newer. It scans local music and now includes YouTube search, AAC/M4A stream playback, and audio downloads to `Music/SimpMusic`, with background playback controls. It does not include all modern SimpMusic features such as login, recommendations, lyrics, or the Compose interface.

- [Source and build instructions](simpmusic-api18/README.md)
- [Download the debug APK](simpmusic-api18/SimpMusic-API18-debug.apk)
- Package: `com.maxrave.simpmusic.legacy`
- Minimum SDK: 18; target SDK: 28
- APK version: `0.9.0-api18-nanojson-stream-compat` (version code 9)
- APK SHA-256: `d80a3d5c49d2931dae4e554b092dcd75e9672cfa80cd25a6a2729e1a93d27c8b`
- [REA reverse-engineering findings and API 18 porting plan](SIMPMUSIC_API18_REA.md)

This reduced edition is provided separately from nmusic Legacy. SimpMusic-derived distribution is under GPL-3.0; see [LICENSE](simpmusic-api18/LICENSE). YouTube Music search uses SimpMusic’s WEB_REMIX search endpoint and response shape; fixture tests cover the changed song ID fields. The bundled NewPipe stream extractor's newer Java collection and Objects APIs are backported for Android 4.3, and nanojson explicitly declares the desugared `JsonArray.stream()` method required during extraction. Playback downloads an AAC/M4A copy to the app cache before using the native player. A download failure opens a dialog with its full stack trace. Live stream extraction and playback/download still need a physical Android 4.3 device test.
