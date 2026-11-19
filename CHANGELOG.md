# Changelog

## 1.0.1 — 2026-11-19

- Accept complete browser Cookies containing JSON, quoted values and duplicate optional fields in both phone Settings and TV QR pairing. Only `auth_token` and `ct0` are used for login.
- Provide a retry when secure storage is unavailable. When saved accounts cannot be decrypted, account management offers a reset that requires confirmation before removing local accounts.

## 1.0.0 — 2026-11-17

- Introduce the selected Play X icon, adaptive/legacy launchers, monochrome layer and matching TV banner.
- Pin official stable GitHub Actions to full SHAs and Node to 26.13.1. Upgrade AGP to 9.5.1, Gradle to 9.9.1, Build Tools to 36.0.0 and Kotlin to 2.4.21; migrate Android modules to built-in Kotlin. Upgrade coroutines core/test to 1.11.0 per Dependabot #1 and declare the application coroutine dependency explicitly. Keep minSdk23, compile37/target35 and JDK17.
- Build the distributable APK in GitHub Actions with encrypted signing Secrets; include redacted phone/TV screenshots via relative README paths and publish a single initial source commit.

- Phone pull-to-refresh, TV refresh button, unified creator/avatar corners and resource-based theme/languages. First launch selects a supported system language or English; Settings has no system option. Remove local sensitive-content hiding. Creator videos now include reposts via locally verified UserTweets GET and cursor pagination.

- Add global video search, own liked videos, and paginated following/creator-video pages; remove photo filters and full-screen photo viewing. Test real query methods, payloads and cursors locally before UI acceptance; SearchTimeline uses POST, Likes/Following/UserMedia use GET. Keep account data and temporary server failures explicit.
- Fix phone touch clicks across settings, feed cards, filters and player controls with a shared touch-aware TV button. Add shimmer skeletons for feed/images and animated progress for restore, pagination, identity verification and proxy checks. Validate coordinate taps separately from accessibility actions.
- One application for phones, tablets and TVs; minSdk23, target35, optional Leanback and standard/TV launcher entries.
- Chinese/English Android resources and in-app Chinese/English selection with first-run system-language matching; bundled QR page follows browser language. Adaptive grid, wrapping controls, consistent dark cards, TV focus and phone Settings entry.
- Phone Settings accept a masked complete Cookie in memory. Verify the X web identity and require explicit confirmation before Android Keystore AES-GCM storage. TV keeps LAN QR pairing, code comparison and encrypted submission; no browser-database import, relay or paid API.
- Real following media via X web operation discovery, cursor pagination, bounded lazy cards, native translation and Media3 MP4/HLS quality/speed/time-bar controls.
- Prefer hardware MediaCodec. Hold the right video surface to boost temporarily; release/cancel restores the original speed. Double-tap left/right to seek with separate 5/10/15/30/60-second settings. Previous/next video follows the filtered feed and stops at queue edges.
- Shared direct/HTTP/SOCKS5 policy, explicit diagnostics, no direct fallback, bounded images/video buffers and background/player release. Runtime pressure and broad TV/API matrices remain pending.
- Enable R8 and resource shrinking for Release and a locally signed, non-debuggable optimized build. APK size falls from 13.78 MB to 2.72 MB (80.28%); both languages, ABIs and bundled pairing cryptography remain included. See verification for optimized-runtime checks and limits.
- Current build, unit, protocol and device evidence is in docs/development/verification.md; GitHub build and signing configuration is in docs/development/github-build.md.
