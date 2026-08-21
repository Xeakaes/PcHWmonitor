# TODO — PC HW Monitor

## F-Droid

- [ ] Fdroid MR !44635 — maintainer `waiting-for-upstream` etiketini kaldırsın (kanıtlı yorum atıldı: 2026-08-20, APK hash'leri + yeşil pipeline linki)
- [ ] Fdroid onayı gelince sürümü güncelleme MR'ı olarak gönder (1.4 → güncel)

## v1.5 (fdroid onayı hangi sürüme denk gelirse o sürüm "big update" olarak tanıtılır)

- [x] **Material You theming**: Dinamik renk özütleme (duvar kağıdından), mevcut palet sistemi + açık/koyu ile bütünleşik
- [x] **Yerel ağ keşfi**: Aynı LAN'daki PC sunucusunu otomatik bul (IP elle girme derdi biter)
- [x] **Bildirim iyileştirmeleri**: Kalıcı bildirimde ana metrikler, genişletilebilir bildirim detayları
- [x] **Yeni renk paletleri**: Daha fazla palet seçeneği, kullanıcı özelleştirilebilir paletler

## v1.6 (planlanan)

- [ ] **Çoklu PC desteği**: Birden fazla PC'ye aynı anda bağlan
- [ ] **TLS/SSL güvenli bağlantı**: Şifreli WebSocket

## Backlog

- [ ] Better simulation mode: `--simulate` için daha gerçekçi donanım profilleri
- [ ] FPS improvements: `--fps-process` otomatik oyun algılama, PresentMon entegrasyonu
- [ ] Disk I/O improvements: okuma/yazma gecikmesi metrikleri, SMART verisi
- [ ] Network enhancements: bant genişliği geçmişi, arayüz bazlı istatistikler
- [ ] Web API v2: geçmiş veri + push bildirimi endpoint'leri
- [ ] Home screen widget: hızlı istatistik widget'ı
- [ ] Offline mode: son bilinen veriyi önbellekle, sunucusuz çalış
- [ ] Background updates: uygulama arka plandayken bağlantıyı sürdür
- [ ] Custom card layouts: kullanıcı tanımlı kart konumu/boyutu
- [ ] Advanced chart options: logaritmik ölçek, karşılaştırmalı grafikler, sensör filtreleme
- [ ] Widget options: widget'ta görünecek veriyi yapılandır
- [ ] Landscape improvements: tabletler için çok sütunlu düzen iyileştirmeleri
- [ ] WebRTC support: WebSocket'e alternatif düşük gecikme
- [ ] QUIC protocol: deneysel taşıma katmanı
- [ ] Local network discovery: (v1.5'e taşındı) ✅
- [ ] Sensor coverage: daha fazla sensör desteği (voltaj, güç fazları vb.)
- [ ] DLL updates: gömülü LibreHardwareMonitorLib.dll güncellemesi
- [ ] New sensor types: yeni donanım izleme özellikleri
- [ ] GitHub Actions CI: hem Android hem sunucu için CI iyileştirmeleri
- [ ] Multiple architecture support: ARM64, x86_64 build'leri
- [ ] ProGuard/R8 optimization: release build optimizasyonu
- [ ] Automated testing: test kapsamını genişlet
- [ ] Video tutorials: Windows ve Android kurulum videoları
- [ ] API documentation: komple WebSocket protokol referansı
- [ ] Troubleshooting guide: genişletilmiş SSS
- [ ] Localization: daha fazla dil çevirisi
- [ ] Cross-platform host: Linux ve macOS sunucu desteği
- [ ] Browser-based config: PC sunucusu için web yapılandırma arayüzü
- [ ] Cloud sync: opsiyonel şifreli geçmiş senkronizasyonu
- [ ] Plugin system: genişletilebilir sensör/plugin mimarisi
