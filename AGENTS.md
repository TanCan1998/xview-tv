# Project rules

Read README_zh-CN.md and docs/development/verification.md before making claims about completion.

- Kotlin, JDK 17, Gradle Kotlin DSL; versions belong in the version catalog.
- Feature modules depend on domain/model, never on concrete providers, network or Room.
- Domain/model/provider contracts are pure JVM Kotlin, independent of Android UI.
- All future remote traffic, including Coil and Media3, must use the shared OkHttp network policy.
- Provider capability failures must be explicit. Never silently replace account data with guest/demo data.
- Keep cursors opaque outside providers. Isolate future account caches by account ID.
- No production tokens, proxy passwords, signing keys or sensitive URLs in Git/logs.
- TV controls require visible focus, D-Pad operation and Back/focus restoration tests.
- The application has a single package and no Demo flavor. provider:demo is test-only and must stay deterministic; never package synthetic account content into the app.
- TV Home uses Menu to show/hide only Settings; D-Pad arrows navigate content normally. Login and Proxy are Settings children; Back returns child → Settings → Home.
- TV playback: hidden controls accept Left/Right seek with held repeats and OK play/pause; no transport buttons. Progress/quality controls auto-hide. Speed/queue buttons live in a compact toolbar outside the video area.
- Hilt/Room remain roadmap items. Encrypted multi-account save/switch/remove (up to 20, legacy single-account compatible) now exists; switching rebuilds account-scoped clients and UI state. Shared OkHttp, LAN pairing, web identity, API23+ Keystore, real following timeline, global video search, own liked videos, following list/creator videos, X-native translation, modern lazy video cards and Media3 standard PlayerView now exist. Query methods/cursors were tested locally before UI acceptance; see video-library-api.md and verification.md for the actually tested scope.
- TV account login uses LAN QR pairing, per docs/adr/006-qr-cookie-login.md: TV serves its bundled page, one complete Cookie input, device-code confirmation, encrypted submission and TV identity confirmation. Phones accept one complete Cookie directly in Settings; parse in memory, verify web identity and require account confirmation before encrypted saving. No public relay, browser-database import or paid/Android private API. Real session material must never enter Git/logs.
- Run `python3 tools/scripts/verify_project.py`; on a compatible host run the README Gradle checks.
- Phone pairing lives in web/pairing; run `node web/pairing/tests/node.mjs` and `node web/pairing/tests/lan-node.mjs` (Node20+). Automated tests use synthetic credentials. Encrypted submission alone is not login; require verified identity and TV confirmation.
- Record unexecuted tests honestly. APK/TV/CI success needs actual evidence.
- Local Android downloads in this workspace must use FluxDown, regardless of file size.
- Use feature/fix/docs branches after the initial import. Publishing, installing and device tests are separate tasks.
