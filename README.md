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

A separate, limited local-music player built for Android 4.3 (API 18) and newer. It scans music stored on the device and supports search, playback controls, seeking, and background playback. It does not include SimpMusic's online streaming or account features.

- [Source and build instructions](simpmusic-api18/README.md)
- [Download the debug APK](simpmusic-api18/SimpMusic-API18-debug.apk)
- Package: `com.maxrave.simpmusic.legacy`
- Minimum SDK: 18; target SDK: 28
- APK SHA-256: `60556d45b655c59b9d62fc07c3bd04a9d98cf268208bd54913fc5586a1c43dca`

This reduced edition is provided separately from nmusic Legacy. SimpMusic-derived distribution is under GPL-3.0; see [LICENSE](simpmusic-api18/LICENSE). Device runtime playback has not yet been verified on physical hardware.
