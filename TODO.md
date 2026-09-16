# TODO — PC HW Monitor

## F-Droid

- [x] Fdroid MR !44635 — CI pipeline başarılı (2026-09-15), metadata base64 hatası düzeltildi, `Binaries` kaldırıldı (reproducible build karşılaştırması başarısız olacağı için)
- [ ] Fdroid onayı gelince sürümü güncelleme MR'ı olarak gönder (v1.6 → güncel)

## v1.6 — Shipped ✅

- [x] **Custom Background + Glassmorphism**: Kullanıcı galeriden resim seçer → tema renkleri otomatik çıkarılır, kartlar şaffaf + blur modu
- [x] **Çoklu PC desteği**: Birden fazla PC'ye aynı anda bağlan, sekmeler arası geçiş
- [x] **TLS/SSL güvenli bağlantı**: Şifreli WebSocket, self-signed sertifika onay dialogu
- [x] **Cloudflare Tunnel**: `--tunnel quick` / `--tunnel <name>` ile kalıcı URL ile uzaktan erişim
- [x] **Güvenlik katmanları**: HMAC timing-safe token karşılaştırması, cert trust dialog, bağlantı limitleri
- [x] **9 renk paleti**: Default, Ocean, Ember, Forest, Black & Gold, Material You, Midnight, Sunset, Arctic
- [x] **Dashboard düzenleme modu**: Kartları yeniden sırala, gizle/göster, ilk ekranda sabitle, yarım/tam genişlik
- [x] **Yatay mod**: Kompakt ızgara, otomatik gizlenen nav bar
- [x] **F-Droid gönderimi**: MR !44635, CI başarılı, onay bekleniyor
- [x] **Bug fixes**: Sunucu geçiş yarış koşulu, temp dosya race condition, clipboard injection, WebSocket yavaş istemci, socket sızıntısı, Room DB migration v2
- [x] **Yerelleştirme**: 14 dil, tüm string'ler çevrildi

## Backlog — v1.7+

### Sunucu

- [ ] Better simulation mode: `--simulate` için daha gerçekçi donanım profilleri
- [ ] FPS improvements: `--fps-process` otomatik oyun algılama
- [ ] Disk I/O improvements: okuma/yazma gecikmesi metrikleri, SMART verisi
- [ ] Network enhancements: bant genişliği geçmişi, arayüz bazlı istatistikler
- [ ] Web API v2: geçmiş veri + push bildirimi endpoint'leri

### Android

- [ ] Home screen widget: hızlı istatistik widget'ı
- [ ] Offline mode: son bilinen veriyi önbellekle, sunucusuz çalış
- [ ] Background updates: uygulama arka plandayken bağlantıyı sürdür
- [ ] Custom card layouts: kullanıcı tanımlı kart konumu/boyutu
- [ ] Advanced chart options: logaritmik ölçek, karşılaştırmalı grafikler, sensör filtreleme

### Bağlantı & Protokol

- [ ] WebRTC support: WebSocket'e alternatif düşük gecikme
- [ ] QUIC protocol: deneysel taşıma katmanı

### LibreHardwareMonitor

- [ ] Sensor coverage: daha fazla sensör desteği (voltaj, güç fazları vb.)
- [ ] DLL updates: gömülü LibreHardwareMonitorLib.dll güncellemesi
- [ ] New sensor types: yeni donanım izleme özellikleri

### Build & Dağıtım

- [ ] Multiple architecture support: ARM64, x86_64 build'leri
- [ ] Automated testing: test kapsamını genişlet (şu an 127 test)

### Dokümantasyon

- [ ] Video tutorials: Windows ve Android kurulum videoları
- [ ] API documentation: komple WebSocket protokol referansı
- [ ] Troubleshooting guide: genişletilmiş SSS

### Uzun Vadeli (v2.0)

- [ ] Cross-platform host: Linux ve macOS sunucu desteği
- [ ] Browser-based config: PC sunucusu için web yapılandırma arayüzü
- [ ] Cloud sync: opsiyonel şifreli geçmiş senkronizasyonu
- [ ] Plugin system: genişletilebilir sensör/plugin mimarisi
