# PC HW Monitor - Roadmap

> Vizyon dokümanı — sürüm bazlı görev takibi için [TODO.md](TODO.md)'ye bakın.

## Vision

### Server-Side Improvements (v1.5+)

- [ ] **Better simulation mode**: Enhance `--simulate` mode with more realistic hardware profiles
- [ ] **FPS improvements**: Add `--fps-process` auto-detection for common games, improve PresentMon integration
- [ ] **Disk I/O improvements**: Add read/write latency metrics, SMART data support
- [ ] **Network enhancements**: Add bandwidth history, per-interface statistics
- [ ] **Web API v2**: Add new endpoints for historical data, push notifications (v1.6: TLS/SSL)

### Android App Features (v1.5 since: Material You, local discovery, notifications, new palettes)

- [ ] **Custom widget**: Add home screen widget for quick stats
- [ ] **Notification improvements**: Add ongoing notification with key metrics, expandable notification details (v1.5)
- [ ] **Material You theming**: Dynamic color extraction from wallpaper (v1.5)
- [ ] **Offline mode**: Cache last known stats, work without server connection
- [ ] **Multiple PC support**: Connect to multiple PCs simultaneously (v1.6)
- [ ] **Background updates**: Keep connection alive when app is in background
- [ ] **Local network discovery**: Auto-detect PC on same network (v1.5)

### Dashboard & UI Enhancements

- [ ] **Custom card layouts**: User-defined card positions and sizes
- [ ] **Advanced chart options**: Logarithmic scale, comparative charts, per-sensor filtering
- [ ] **Widget options**: Configurable what appears on widget/home screen
- [ ] **Landscape improvements**: Better multi-column layout for tablets
- [ ] **Dark mode palette deepening**: More color palette options, user-customizable palettes (v1.5)

### Connectivity & Protocol

- [ ] **WebRTC support**: Alternative to WebSocket for lower latency
- [ ] **QUIC protocol**: Experimental transport layer
- [ ] **Secure connection**: TLS/SSL for WebSocket connection (v1.6)
- [ ] **Local network discovery**: Auto-detect PC on same network (v1.5)

### LibreHardwareMonitor Integration
- [ ] **Sensor coverage**: Add support for more sensors (voltage, power phases, etc.)
- [ ] **DLL updates**: Update embedded LibreHardwareMonitorLib.dll to latest version
- [ ] **New sensor types**: Support for newer hardware monitoring features

### Build & Distribution
- [x] **Fdroid support**: Add Fdroid metadata and reproducible builds (in progress: MR !44635, TODO.md'de takip)
- [x] **GitHub Actions CI**: Enhance CI for both Android and server
- [ ] **Multiple architecture support**: ARM64, x86_64 builds
- [x] **ProGuard/R8 optimization**: Release builds optimization
- [x] **Automated testing**: Expand test coverage

### Documentation
- [ ] **Video tutorials**: Setup guides for Windows and Android
- [ ] **API documentation**: Complete WebSocket protocol reference
- [ ] **Troubleshooting guide**: Expanded FAQ with common solutions
- [ ] **Localization**: Add more language translations

### Long-term Vision (v2.0)
- [ ] **Cross-platform host**: Linux and macOS support for the server
- [ ] **Browser-based config**: Web interface for PC server configuration
- [ ] **Cloud sync**: Optional encrypted sync of historical data
- [ ] **Plugin system**: Extensible sensor/plugin architecture
