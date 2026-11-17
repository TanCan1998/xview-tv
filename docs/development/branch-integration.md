# Branch integration

All development work is consolidated into the initial XView 1.0.0 source snapshot on `main`. Earlier local development history is retained outside the repository as a private Git bundle and is not published.

Dependency updates incorporated from the repository's Dependabot branches on 2026-11-17:

| Dependency | Integrated version |
| --- | --- |
| Android Gradle Plugin | 9.5.1 |
| Gradle Wrapper | 9.9.1 |
| Kotlin / Compose compiler | 2.4.21 |
| kotlinx.coroutines core / test | 1.11.0 |
| Compose BOM | 2026.09.00 |
| AndroidX TV Material | 1.1.0 |
| OkHttp | 5.5.1 |
| ZXing core | 3.5.4 |
| org.json | 20260814 |

AGP requires at least Gradle 9.6.0 and Build Tools 36.0.0. Android modules use built-in Kotlin; JVM modules retain the Kotlin JVM plugin. Both Kotlin plugin artifacts use the version catalog's single Kotlin version. Wrapper distribution and JAR checksums are validated by the structural check.

The final public history is intentionally squashed; it does not preserve the individual development or Dependabot commits. The branch changes are integrated into the source before branch removal. Runtime and CI outcomes are recorded separately in [verification](verification.md).
