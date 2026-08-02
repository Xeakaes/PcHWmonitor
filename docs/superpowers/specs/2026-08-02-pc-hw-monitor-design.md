# PC HW Monitor — Tasarım Dokümanı

Tarih: 2026-08-02
Durum: Onaylandı (kullanıcı onay kapılarını atladı, doğrudan inşa edilecek)

## Amaç

Yerel Wi-Fi ağındaki bir Windows PC'nin anlık sistem verilerini (CPU/GPU sıcaklık ve kullanım, RAM/VRAM, saat hızları) telefonda modern bir dashboard olarak gösteren Android uygulaması + veri köprüsü olarak çalışan Python sunucusu.

## Kapsam

- **Android (Kotlin, Jetpack Compose, Material 3)**: 3 sekmeli uygulama — Dashboard / Geçmiş / Ayarlar. 13 dil desteği. Açık/koyu/sistem teması. WebSocket ile anlık veri. Room DB ile 1 saatlik geçmiş.
- **Python sunucusu (FastAPI)**: Aynı bilgisayarda çalışan LibreHardwareMonitor'un `/data.json` API'sini okuyup normalize eden ve WebSocket üzerinden yayınlayan köprü. `--simulate` modu ile gerçek PC olmadan sahte veri üretir.
- FPS: v1'de JSON şemasında `fps` alanı `null`; sonraki sürüme bırakıldı.

## Mimari

```
Windows PC
  LibreHardwareMonitor (Remote Web Server: 8085)
        │ HTTP GET /data.json (her 1 sn)
  Python FastAPI sunucu (port 8765, aynı PC)
    ├─ LHMAdapter    : ham sensör JSON → normalize şema
    ├─ Simulator     : --simulate modunda gerçekçi sahte veri
    └─ Hub           : bağlı istemcilere her 1 sn yayın
        │ WebSocket ws://<pc-ip>:8765/ws
Android
  OkHttp WebSocketClient → Flow<SystemStatus>
  MonitorViewModel (StateFlow) → Compose UI
  Room DB ← 5 sn'de bir örnek, 1 saat tutulur
```

Android yalnızca sunucuyla konuşur; LHM ile doğrudan teması yoktur.

## WebSocket Protokolü

Sunucu → istemci, JSON, her saniye bir `status` mesajı; bağlantıda önce `welcome`:

```json
{ "type": "welcome", "intervalMs": 1000, "serverName": "DESKTOP-ABC",
  "source": "librehardwaremonitor", "pcName": "DESKTOP-ABC" }

{ "type": "status", "timestamp": 1754150000,
  "cpu":   { "name": "Intel Core i7-13700K", "usagePct": 34.5, "tempC": 61.2,
             "clockMhz": 5100.0, "powerW": 125.0, "loads": [12,45,33] },
  "gpu":   { "name": "NVIDIA GeForce RTX 4070", "usagePct": 78.3, "tempC": 71.4,
             "hotspotC": 84.1, "vramUsedMb": 6112, "vramTotalMb": 12288,
             "coreClockMhz": 2745.0, "memClockMhz": 10500.0, "powerW": 182.0,
             "fps": null },
  "ram":   { "usedGb": 11.2, "totalGb": 32.0, "usagePct": 35.0, "clockMhz": 3600.0 } }
```

- Bilinmeyen/eksik alanlar `null` (sıcaklık yoksa vb.). UI null'ı "—" gösterir.
- Sunucu LHM verisi alamazsa `status` mesajını `"available": false` + hata koduyla gönderir, `cpu/gpu/ram` null olur.

## Android Uygulaması

### Paket yapısı `com.example.pchwmonitor`
- `data/network/` — `WebSocketClient` (OkHttp), `StatusParser` (kotlinx.serialization)
- `data/local/` — `HistoryDb` (Room), `HistoryDao`, `HistorySample` entity, `HistoryRepository`
- `data/SettingsStore.kt` — DataStore: serverIp, port, tema, otomatik bağlan
- `domain/model/` — `SystemStatus`, `CpuInfo`, `GpuInfo`, `RamInfo` (immutable, @Serializable)
- `ui/` — theme (mevcut), navigation (alt bar 3 sekme), `dashboard/`, `history/`, `settings/`, `components/`
- `MonitorViewModel.kt` — WebSocket'tan gelen durumu StateFlow yapar; bağlantı durumu, otomatik yeniden bağlanma (üstel geri çekilme); geçmişi Room'a yazar
- `PcHwMonitorApp.kt` — Application sınıfı, basit elle DI (repo/VM fabrikaları)

### Ekranlar
1. **Dashboard**: Üst durum çubuğu (bağlantı durumu, PC adı, veri kaynağı). Kartlar:
   - CPU kartı: sıcaklık (renkli radyal gösterge), kullanım %, çekirdek hızı, güç, çekirdek yükü şeridi
   - GPU kartı: sıcaklık + hotspot, kullanım %, VRAM kullanım (dolum barı), çekirdek/mem saat hızı, güç
   - RAM kartı: kullanım % + dolum barı, kullanılan/toplam GB, saat hızı
   - Her kartta 60 sn'lik mini çizgi grafik (canlı, Canvas)
   - Renk mantığı: yeşil <60°C / sarı 60–75 / turuncu 75–85 / kırmızı >85 (GPU hotspot için +10 eşik)
2. **Geçmiş**: Metrik seçici (CPU sıcaklık, GPU sıcaklık, GPU hotspot, CPU kullanım, GPU kullanım, RAM), 1 saatlik çizgi grafik (Canvas), dönem içi min/maks bilgisi. Veriler Room'dan.
3. **Ayarlar**: Sunucu IP, port, tema seçimi (sistem/açık/koyu), kaydet + bağlan. Dil sisteme göre (13 dil kaynağı). Sürüm bilgisi.

### Grafikler
Harici kütüphane yok — özel Canvas bileşenleri: `RadialGauge` (yarım/çeyrek daire sıcaklık göstergesi), `LineChart` (mini + geçmiş), `FilledBar` (VRAM/RAM). Bağımlılık yükü az, temayla birebir uyumlu.

### Room şeması
`history_samples(timestamp INTEGER PK, cpuTempC REAL, cpuUsagePct REAL, gpuTempC REAL, gpuUsagePct REAL, gpuHotspotC REAL, ramUsagePct REAL)` — 5 sn'de bir insert, >1 saat kayıt silinir.

### Bağımlılıklar (eklenecek)
OkHttp, kotlinx-serialization-json, Room (runtime/ktx/compiler), DataStore-preferences, lifecycle-viewmodel-compose, navigation-compose, coroutines, ksp (Room compiler).

### Localization
Android string kaynakları: values (EN varsayılan), fr, de, es, it, pt, pt-rBR, ru, tr, pl, nl, zh, zh-rTW, ja. Toplam 13 dil + İngilizce.

### Hata yönetimi
- Bağlantı kopması → durum çubuğu "Bağlanıyor/Yeniden bağlanıyor", üstel geri çekilme (1s→30s).
- Sunucu "available:false" → kullanıcıya sunucu hatası gösterilir (LHM kapalı).
- INTERNET + ACCESS_NETWORK_STATE izinleri; cleartext trafik için `usesCleartextTraffic` (yalnızca debug için networkSecurityConfig yerine manifest flag — yerel ağ HTTP olduğu için gerekli).

## Python Sunucusu (`server/`)

```
server/
├── main.py           # FastAPI app, /ws endpoint, --port, --simulate, --lhm-url
├── schema.py         # Pydantic modeller (welcome, status)
├── adapters/lhm.py   # LHMAdapter: /data.json'ı okur, sensör eşleştirir
├── adapters/simulator.py  # Simulator: deterministik+gerçekçi sahte veri
└── hub.py            # WebSocketHub: istemci kaydı, 1 sn broadcast
```

- LHM eşleştirme: `/data.json` ağacını yürür; HardwareType (CPU/GpuNvidia/Memory) + SensorType (Temperature/Load/Clock/Data/SmallData/Power) + isim eşleşmesi. Örn: "CPU Package"/"CPU Core Max" → tempC, "CPU Total" → usagePct, "Core Max" → clockMhz, "GPU Hot Spot" → hotspotC, "GPU Memory Used"/"GPU Memory Total" → VRAM, "GPU Core Clock"/"GPU Memory Clock" → hızlar, "Memory Used"/"Memory Total"/"Memory Clock" → RAM. Eşleşemeyen alan null.
- Simulator: sinüs + gürültü ile 30–90°C sıcaklıklar, 5–100% kullanım, gerçekçi saat hızları; sistem saatine göre ilerler, her çağrıda farklı değer üretir (rastgele, sabit seed yok).
- `hub.py`: bağlı WebSocket'leri set'te tutar, her 1 sn mevcut durumu broadcast eder; LHM yanıt vermezse `available:false` yayınlar (sunucu çökmez, 5 sn sonra yeniden dener).
- CORS/ekstra uç yok; tek uç `/ws`. Ayrıca sağlık kontrolü `GET /health`.

## Testler

- **Python (pytest)**: schema validasyonu (örnek JSON'lar), simulator determinizmi (aralık kontrolü), LHM adapter parsing — `server/tests/fixtures/lhm_sample.json` (gerçekçi örnek LHM yanıtı) ile sensör eşleştirme testi.
- **Android (JUnit, Robolectric'sız saf unit)**: `StatusParser` JSON decode testleri (null alanlar, eksik alanlar, tam mesaj), HistoryRepository fake DAO ile insert/prune testi, MonitorViewModel fake client ile bağlantı durumu testi.
- **Build doğrulama**: `./gradlew assembleDebug` + `./gradlew test`. Sunucu: `python -m pytest server/tests`.
- Manuel: `python server/main.py --simulate` → `ws://127.0.0.1:8765/ws`'e bağlanan test istemcisi (websocat veya küçük python script) mesajları doğrular.

## Kapsam Dışı (v1)

- FPS (şema hazır, null)
- Birden fazla PC, mDNS otomatik bulma
- Bildirimler/alarmlar (belirli sıcaklıkta push)
- Açılışta otomatik bağlanma kilit ekranı widget'ı
