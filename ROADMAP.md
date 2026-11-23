# PC HW Monitor - Roadmap

> Vizyon dokümanı — sürüm bazlı görev takibi için [TODO.md](TODO.md)'ye bakın.

## Shipped — v1.5

- [x] Material You dynamic theming, 8 color palettes
- [x] Ongoing notification with live metrics + expandable details
- [x] Connection method picker: Manual (IP + port) / Network scan, dedicated Connect button
- [x] **QR quick connect**: server tray shows a QR (`pchw://connect?ip&port&token`), phone scans and fills everything automatically
- [x] LAN discovery hardening: multicast lock, subnet broadcast, real-adapter IP selection
- [x] Visible connection/auth errors, non-blocking hardware sampling on the server
- [x] PresentMon re-embedded into the packaged EXE (FPS card)

## Shipped — v1.6

- [x] Custom Background + Glassmorphism: User picks an image → auto-extract theme colors, cards become semi-transparent with blur effect
- [x] Cloudflare Named Tunnel: Persistent URL for remote access (no port forwarding needed)
- [x] TLS/SSL support: optional encrypted WebSocket connections (`wss://`) with self-signed cert trust dialog
- [x] Multi-PC support: Connect to multiple PCs simultaneously, switch via tabs on the dashboard
- [x] Security hardening: HMAC timing-safe token comparison, cert trust dialog, connection limits
- [x] 9 color palettes (Default, Ocean, Ember, Forest, Black & Gold, Material You, Midnight, Sunset, Arctic)
- [x] Dashboard edit mode: reorder, hide, pin cards; toggle half/full width
- [x] Landscape mode: compact scroll-free grid, auto-hiding nav bar
- [x] F-Droid submission: MR !44635 (CI green, merged 2026-11-04)
- [x] Bug fixes: server switch race condition, temp file race, clipboard injection, WebSocket slow client, socket leak, Room DB migration v2
- [x] Localization: 14 languages, all strings translated

## Shipped — v1.6.3 (2026-11-04)

- [x] CGNAT address allowlist: `100.64.0.0/10` (RFC 6598) accepted for RFC 6598 shared-space addresses
- [x] Tailscale / NetBird hostnames: dot-boundary suffixes `.ts.net` / `.netbird.cloud` accepted by `MonitorController.isAllowedHost`
- [x] Portrait QR scanner: manifest override for `com.journeyapps.barcodescanner.CaptureActivity`
- [x] Store launches: F-Droid live (v1.6.3, versionCode 11) + Google Play production (2026-11-23, 100% rollout) + GitHub Releases
- [x] Greek localization (community PR #2 by @theoasima) + `el` in the language picker → 15 languages

## Next — v1.6.4

- [ ] **[P1] Plaintext token flow**: don't send auth tokens over plain `ws://` without explicit user consent; keep the insecure-connection warning visible after connect
- [ ] **[P2] UI-thread `runBlocking`**: `ServerEditViewModel.save()` → coroutine with `isSaving` state
- [ ] **[P2] FPS time window**: frame-count cap (`deque(maxlen=1800)`) → timestamp-based real 30 s window with tests
- [ ] **[P3] Hardware compatibility test matrix**: LHM fixture archive, missing/null sensor cases

## Vision — v1.7+

### Server-Side

- [ ] **Linux server support (v1.7)**: Native Linux sensor source via psutil (CPU/RAM/temps/fans, optional NVML GPU), `source=auto` Linux fallback instead of lhm-lib/http, one-file Linux build script with cloudflared-linux embed, optional tray (pystray/AppIndicator), README quickstart. FPS stays Windows-only (PresentMon/ETW). Server core, XDG config path, cloudflared finder and CI smoke already pass on Ubuntu
- [ ] **Better simulation mode**: Enhance `--simulate` mode with more realistic hardware profiles
- [ ] **FPS improvements**: Add `--fps-process` auto-detection for common games
- [ ] **Disk I/O improvements**: Add read/write latency metrics, SMART data support
- [ ] **Network enhancements**: Add bandwidth history, per-interface statistics
- [ ] **Web API v2**: Add new endpoints for historical data, push notifications

### Android App

- [ ] **Custom widget**: Add home screen widget for quick stats
- [ ] **Offline mode**: Cache last known stats, work without server connection
- [ ] **Background updates**: Keep connection alive when app is in background
- [ ] **Custom card layouts**: User-defined card positions and sizes
- [ ] **Advanced chart options**: Logarithmic scale, comparative charts, per-sensor filtering

### Connectivity & Protocol

- [ ] **WebRTC support**: Alternative to WebSocket for lower latency
- [ ] **QUIC protocol**: Experimental transport layer

### LibreHardwareMonitor Integration

- [ ] **Sensor coverage**: Add support for more sensors (voltage, power phases, etc.)
- [ ] **DLL updates**: Update embedded LibreHardwareMonitorLib.dll to latest version
- [ ] **New sensor types**: Support for newer hardware monitoring features

### Build & Distribution

- [ ] **Multiple architecture support**: ARM64, x86_64 builds
- [ ] **Automated testing**: Expand test coverage (currently 141 Android + 78 server tests)

### Documentation

- [ ] **Video tutorials**: Setup guides for Windows and Android
- [ ] **API documentation**: Complete WebSocket protocol reference
- [ ] **Troubleshooting guide**: Expanded FAQ with common solutions

### Long-term Vision (v2.0)

- [ ] **Cross-platform host**: macOS support for the server (Linux ships in v1.7)
- [ ] **Browser-based config**: Web interface for PC server configuration
- [ ] **Cloud sync**: Optional encrypted sync of historical data
- [ ] **Plugin system**: Extensible sensor/plugin architecture
