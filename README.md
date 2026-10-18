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
- APK version: `0.12.0-api18-client-matched-audio-fetch` (version code 12)
- APK SHA-256: `acf4091298dd8c37dfe005eae81575d453960a15d9ae2ca3799d04431077e14a`
- [REA reverse-engineering findings and API 18 porting plan](SIMPMUSIC_API18_REA.md)

This reduced edition is provided separately from nmusic Legacy. SimpMusic-derived distribution is under GPL-3.0; see [LICENSE](simpmusic-api18/LICENSE). YouTube Music search uses SimpMusic’s WEB_REMIX search endpoint and response shape; fixture tests cover the changed song ID fields. The bundled NewPipe stream extractor's newer Java collection and Objects APIs are backported for Android 4.3, and NanoJSON includes the exact desugared `JsonArray.stream()` bridge the extractor invokes. Stream resolution requests iOS player formats when Android has none; media requests use the matching player client user agent and retry other AAC/M4A candidates if a stream URL is rejected. Live extraction and playback/download still need a physical Android 4.3 device test.
