# GitHub build and signing

XView 1.0.0 uses AGP 9.5.1, Gradle 9.9.1, JDK 17, Kotlin 2.4.21, compile SDK 37.2 / target SDK 35, Build Tools 36.0.0 and minSdk 23. Android convention plugins use AGP built-in Kotlin; pure JVM modules continue using the Kotlin JVM plugin. Compose compiler and JVM Kotlin share version 2.4.21. Coroutines core and test are both pinned to 1.11.0, matching Dependabot PR #1; the application declares coroutines directly to avoid an older transitive compile API.

The [CI workflow](../../.github/workflows/ci.yml) runs structure checks, synthetic pairing tests, JVM tests, Android Lint and the R8/resource-shrunk optimized build. All Actions are pinned to full commit SHAs. Official stable versions checked on 2026-11-17:

| Action | Version | Commit |
| --- | --- | --- |
| actions/checkout | 7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| actions/setup-java | 6.3.0 | `d1374f8ac74474200d16ef8ecee22c7ae7a51880` |
| actions/setup-node | 7.1.0 | `949feb2413d6458794dcd2491c4babbbce0c15c1` |
| android-actions/setup-android | 4.0.4 | `be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd` |
| actions/upload-artifact | 7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |

Node is pinned to `26.13.1`, the latest stable release checked on that date. The Node GitHub latest-release API also returned an alpha-named release; pre-release names are excluded when choosing a stable version. Runner image: `ubuntu-24.04`. Android SDK is set up explicitly rather than relying on the runner's preinstalled packages.

## Artifacts

Push/workflow-dispatch builds read `versionName`, `versionCode` and the actual APK path from Gradle's `output-metadata.json`. They produce `xview-<versionName>` containing `xview-<versionName>.apk` and `SHA256SUMS`; application versions are not duplicated in the workflow. Unsafe filename characters are normalized and split/missing APK metadata is rejected. The `validation-reports` artifact contains test and Lint reports. Pull requests run the build and checks without access to signing credentials. Tagged release downloads are published from a successful GitHub build, never a locally compiled APK.

## Signing and forks

The optimized variant is not debuggable and uses a local debug certificate by default. For the original project's 1.0.0 distribution, CI replaces that signature using an encrypted repository Secret containing the existing installation certificate, allowing in-place updates on the tested devices. This is the existing testing certificate, not a newly established production signing identity. No keystore is committed.

Configure these Actions Secrets for signed push builds in your own repository:

- `XVIEW_KEYSTORE_BASE64`: base64-encoded keystore.
- `XVIEW_KEYSTORE_PASSWORD`: keystore password.
- `XVIEW_KEY_ALIAS`: signing alias.
- `XVIEW_KEY_PASSWORD`: private-key password.

Use your own keystore for independent distribution. A fork's APK with a different certificate cannot update the original package in place. Never print secret values, add them to Gradle properties, or include them in artifact paths. Signing uses `apksigner` environment-password inputs; the temporary keystore is removed after signing.

## Upgrade references

- [AGP 9.5 compatibility and fixes](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Gradle release checksums](https://gradle.org/release-checksums/)
- [Node 26.13.1](https://github.com/nodejs/node/releases/tag/v26.13.1)

Wrapper distribution SHA256: `d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228`; Wrapper JAR SHA256: `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8`. JVM compilation remains Java 17; the installation minimum stays Android 6.0.

## Affected checks

CI compares the previous push SHA or PR base SHA with HEAD. Documentation and redacted image changes skip Gradle. App/UI changes build and lint the application without unrelated JVM tests; core changes test the changed module and its transitive dependants. Pairing tests run for pairing changes. Version catalog, Wrapper, build logic and workflow changes run full checks. An unknown base, initial push or manual dispatch also runs full checks. setup-java caches Gradle dependencies; Gradle runs with --build-cache. SDK installation uses Android CLI rather than deprecated sdkmanager.
