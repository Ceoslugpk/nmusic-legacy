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
- APK SHA-256: `7bada16c7d85c9875fe95ffe9353f04ac79a8ac770527c03821a6e380b4a1194`
- [REA reverse-engineering findings and API 18 porting plan](SIMPMUSIC_API18_REA.md)

This reduced edition is provided separately from nmusic Legacy. SimpMusic-derived distribution is under GPL-3.0; see [LICENSE](simpmusic-api18/LICENSE). The updated API 18 build compiles and its APK manifest was verified; YouTube runtime behavior has not yet been tested on an Android 4.3 device.
