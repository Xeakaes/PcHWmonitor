# TODO — PC HW Monitor

## Distribution

- [x] F-Droid MR !44635 — CI pipeline green (2026-09-15), metadata base64 error fixed, `Binaries` removed (reproducible-build comparison would fail)
- [x] F-Droid MR merged (2026-11-04) — v1.6.3 (versionCode 11) live on F-Droid
- [x] Google Play production launch (2026-11-23) — 100% rollout, 177 countries + rest of world
- [ ] Watch install tempo toward the 12-month goal: **5,000 total downloads by 2027-11-14** (Play lifetime + GitHub APK; F-Droid invisible = bonus). ~13/day pace; check monthly around the 14th

## v1.6.3 — Shipped ✅ (2026-11-04)

- [x] **CGNAT allowlist**: `100.64.0.0/10` (RFC 6598) accepted as a valid address
- [x] **Tailscale / NetBird**: dot-boundary suffixes `.ts.net` / `.netbird.cloud` in `MonitorController.isAllowedHost`
- [x] **Portrait QR scanner**: manifest override for `com.journeyapps.barcodescanner.CaptureActivity`
- [x] **Stores**: F-Droid + GitHub + Google Play (production) all live
- [x] **Greek localization**: PR #2 by @theoasima (all base keys, format specifiers match) + `el` added to the language picker → 15 languages

## v1.6.4 — Next

Findings from the technical review notes (verified against v1.6.3 source):

- [ ] **[P1] Plaintext token flow**: `MonitorController` sets `AppError.PlaintextToken` when connecting without TLS, but the connection proceeds anyway and the warning is cleared on success (line 47). Decide default behavior: block token over plaintext unless the user explicitly consents; keep the security warning visible while the connection is insecure; add tests for TLS on/off × token present/absent
- [ ] **[P2] `runBlocking` on the UI thread**: `ServerEditViewModel.save()` (line 62) blocks the main thread on DataStore I/O. Convert to `viewModelScope.launch` + suspend, add `isSaving` state, guard against double-tap duplicate saves
- [ ] **[P2] FPS time window**: `server/adapters/fps.py` caps history at `deque(maxlen=1800)` frames — 30 s only at 60 FPS (7.5 s at 240 FPS). Store timestamps and select the last 30 s; add unit tests for 60/144/240 FPS, empty and irregular frame gaps
- [ ] **[P3] Hardware compatibility tests**: fixture archive of varied LibreHardwareMonitor JSON outputs, Intel/AMD × NVIDIA/AMD/Intel matrix via volunteer reports, null/missing-sensor cases must not crash the app
- [x] **[P3] README/docs refresh**: store badges (Play + F-Droid), live store links, test counts (141 Android / 78 server), 15 languages
- [ ] **Deferred from 1.6.3**: reword `error_address_invalid` in `strings.xml` (14 locales)

## Backlog — v1.7+

### Server

- [ ] **Linux server support (v1.7)**: psutil-based native Linux sensor source (CPU/RAM/temps/fans, optional NVML GPU), `source=auto` Linux fallback, one-file Linux build script + cloudflared-linux embed, optional tray (pystray/AppIndicator), README Linux quickstart. FPS stays Windows-only (PresentMon/ETW). Server core, XDG config path, cloudflared finder and CI smoke already pass on Ubuntu
- [ ] Better simulation mode: more realistic hardware profiles for `--simulate`
- [ ] FPS improvements: `--fps-process` auto game detection
- [ ] Disk I/O improvements: read/write latency metrics, SMART data
- [ ] Network enhancements: bandwidth history, per-interface statistics
- [ ] Web API v2: historical data + push notification endpoints

### Android

- [ ] In-app support links: Buy Me a Coffee (`buymeacoffee.com/Obscrum`) + Patreon entry in Settings/About
- [ ] Home screen widget: quick stats widget
- [ ] Offline mode: cache last known stats, work without the server
- [ ] Background updates: keep the connection alive while the app is backgrounded
- [ ] Custom card layouts: user-defined card position/size
- [ ] Advanced chart options: logarithmic scale, comparative charts, sensor filters

### Connectivity & Protocol

- [ ] WebRTC support: low-latency alternative to WebSocket
- [ ] QUIC protocol: experimental transport layer

### LibreHardwareMonitor

- [ ] Sensor coverage: more sensors (voltage, power phases, etc.)
- [ ] DLL updates: refresh embedded LibreHardwareMonitorLib.dll
- [ ] New sensor types: newer hardware monitoring features

### Build & Distribution

- [ ] Multiple architecture support: ARM64, x86_64 builds
- [ ] Automated testing: expand coverage (currently 141 Android + 78 server tests)

### Documentation

- [ ] Video tutorials: Windows and Android setup videos
- [ ] API documentation: complete WebSocket protocol reference
- [ ] Troubleshooting guide: expanded FAQ

### Long-term (v2.0)

- [ ] Cross-platform host: macOS server support (Linux ships in v1.7)
- [ ] Browser-based config: web configuration UI for the PC server
- [ ] Cloud sync: optional encrypted history sync
- [ ] Plugin system: extensible sensor/plugin architecture
