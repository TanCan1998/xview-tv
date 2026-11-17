# Third-party provenance

- Gradle Wrapper 8.11.1 scripts/JAR: https://github.com/gradle/gradle/tree/v8.11.1 ; Apache-2.0.
- Wrapper JAR official SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
- Gradle binary distribution official SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.
- Upstream checksum source: https://services.gradle.org/distributions/ .
- Apache-2.0 text copied from the local Python packaging distribution's LICENSE.APACHE (standard Apache license text).
- Gradle, AGP, Kotlin, AndroidX and JUnit dependencies are declared, not vendored. Audit resolved transitive dependencies before release.
- OkHttp 4.12.0 / Okio 3.6.0: Maven dependencies, Apache-2.0; https://github.com/square/okhttp and https://github.com/square/okio .
- ZXing core 3.5.3: Maven dependency, Apache-2.0; https://github.com/zxing/zxing .
- node-forge 1.4.3: vendored minified browser build in `web/pairing/vendor/forge-1.4.3.min.js`, used under the BSD-3-Clause option. Upstream https://github.com/digitalbazaar/forge ; package https://registry.npmjs.org/node-forge/-/node-forge-1.4.3.tgz . NPM SHA512 integrity `P6wzfh6t70avmV8FrVEAd/36sOPJQ+hkaJVX1Laap9J/77m7M0eyozDvc8SwnDf4P7MP94gwbAAo0rg3YVXT+g==` verified before extraction. License text retained at [forge-LICENSE](../../web/pairing/vendor/forge-LICENSE), with only the trailing blank line normalized. No CDN or runtime third-party scripts.
- AndroidX Media3 1.5.1 (ExoPlayer/HLS/OkHttp datasource/PlayerView): Maven dependencies, Apache-2.0; https://github.com/androidx/media . Standard controls own playback speed, seek bar, transport and tracks.
- JSON-java 20240303: Maven dependency, public-domain/Unlicense; https://github.com/stleary/JSON-java .
- Just Player TV interaction reference: https://github.com/moneytoo/Player/tree/6df39ae74464ec8b9cb8bb3992ee3ab508513a11 ; root Unlicense, research-only, no copied FFmpeg modules or bundled APK. See [playback design](../../docs/development/media-playback.md).
- Reference projects in the design document are research references only; no code copied.
