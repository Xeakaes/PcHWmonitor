# PC HW Monitor

Bilgisayarının anlık sistem verilerini yerel Wi-Fi üzerinden telefonunda gösteren modern dashboard.
A modern dashboard that shows your PC's real-time hardware stats on your phone over local Wi-Fi.

<a href="https://github.com/Xeakaes/PcHWmonitor/actions/workflows/codeql.yml" target="_blank">
  <img src="https://github.com/Xeakaes/PcHWmonitor/actions/workflows/codeql.yml/badge.svg" alt="CodeQL Status">
</a>
<a href="https://alternativeto.net/software/pc-hw-monitor/about/" target="_blank">
  <img src="https://img.shields.io/badge/AlternativeTo-Listed-blue" alt="AlternativesTo Page">
</a>
<a href="https://xeakaes.github.io/PcHWmonitor/" target="_blank">
  <img src="https://img.shields.io/badge/Website-Visit-brightgreen" alt="Project Website">
</a>
[![Android Weekly](https://raw.githubusercontent.com/Xeakaes/PcHWmonitor/main/assets/aw-badge.svg)](https://androidweekly.net/issues/issue-739)


**Lisans / License:** [AGPL-3.0](LICENSE) — EXE, AGPL-3.0 lisanslı [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) DLL'i paketler.

**Support:** [Patreon](https://www.patreon.com/cw/Obscrum) — free software stays free thanks to supporters.
**Destek:** [Patreon](https://www.patreon.com/cw/Obscrum) — ücretsiz yazılım, destekçiler sayesinde ücretsiz kalır.

---

## English

### Features

- **Android** (Kotlin, Jetpack Compose, Material 3):
   - CPU / GPU / iGPU / RAM cards — temperatures, usage, clock speeds, power, VRAM/RAM, core loads
   - Disk, Network, Fan, and FPS cards — disk usage & throughput, net up/down, fan RPM, game FPS with 1% low
   - Live charts, configurable chart window (30s / 60s / 300s), 1-hour history (Room DB), custom logo
   - **5 color palettes** (Default, Ocean, Ember, Forest, Black & Gold) — light/dark variants, applies instantly from Settings
   - **Dashboard edit mode**: reorder cards, hide/unhide cards, pin cards to the first screen, and toggle each card between half/full width
   - **Landscape mode: compact scroll-free grid on one screen**, with the nav bar auto-hiding and reappearing on tap; tablets get a wider multi-column layout
   - 14 languages (selectable in Settings)
- **Windows EXE** (single file): zero-install hardware reading via an embedded `LibreHardwareMonitorLib.dll`; FastAPI + WebSocket streams data to your phone. No PC hardware? `--simulate` mode generates realistic fake data. Disk/Network stats come from `psutil`; packaged FPS support uses an embedded `PresentMon64.exe` (see Building the EXE).

### Architecture

```
Windows PC
  PcHwMonitor.exe (port 8765)
    ├─ default: embedded LibreHardwareMonitorLib (in-process reading, --uac-admin manifest)
    └─ or: LibreHardwareMonitor Remote Web Server (8085) → --source http
        │ WebSocket ws://<pc-ip>:8765/ws  (one status message every second)
        │ CPU/GPU/RAM/igpu from LHM; Disk/Network from psutil; FPS from PresentMon64
Android app (same Wi-Fi)
```

The Android app only talks to the server; it never touches LibreHardwareMonitor directly.

### Windows EXE (recommended path)

1. Run `dist\PcHwMonitor.exe`, accept the UAC prompt (admin rights are needed for CPU temperature).
2. On your phone, open the **Settings** tab, enter the PC's Wi-Fi IP (e.g. `192.168.1.50`) and port (`8765`), press **Save**. The dashboard connects automatically.
3. The EXE sits in the system tray (next to the clock); right-click → **Kapat** (Close) shuts the server down. The phone shows "Bağlantı yok" (No connection) when it is offline.

To rebuild, run `build_exe.bat` at the project root on Windows (needs pythonnet + pyinstaller; it copies the DLLs into `server\vendor` and bundles them). In packaged mode, errors are logged to `dist\pchw.log`.

`psutil` (used for Disk and Network sensors) is listed in `server/requirements.txt`. Packaged FPS support additionally requires `server/presentmon/PresentMon64.exe`; `build_exe.bat` copies it into `dist/` when present and disables FPS otherwise.

### Server Setup (development)

**Windows (real data):**

```bash
cd server
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt -r requirements-dev.txt   # pythonnet via requirements-dev
.venv\Scripts\python main.py
```

Options: `--port <8765>` · `--source <auto|http|lib>` · `--lhm-url <http://127.0.0.1:8085/data.json>` · `--interval <ms>` · `--simulate` · `--fps-process <name>`

- `--source auto` (default): uses the embedded DLL if present (`lib`), falls back to the LHM web server.
- `--source lib`: reads `LibreHardwareMonitorLib.dll` in-process (DLL path can be set via the `LHMDIR` env var).
- `--source http`: reads the JSON API of an external LibreHardwareMonitor (Options → Remote Web Server, port 8085).
- `--fps-process <name>`: targets a specific process for frame capture (e.g. `--fps-process game.exe`). Omit it (or pass empty) and PresentMon auto-follows the active fullscreen process. Requires `server/presentmon/PresentMon64.exe`; if missing, FPS is disabled and `fps` comes back as `null`.

**Simulation mode (for testing, any OS):**

```bash
cd server
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python main.py --simulate --port 8765
```

**Verify the server:**

```bash
curl http://localhost:8765/health          # {"ok":true,"source":"..."}
.venv/bin/python smoke_test.py             # verifies welcome + 3 status messages
.venv/bin/python -m pytest tests -v        # 37 tests
```

### Android Setup

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Tests: `./gradlew :app:testDebugUnitTest`

### WebSocket Protocol

One `status` message per second from server to client; `welcome` comes first on connect:

```json
{"type":"welcome","intervalMs":1000,"serverName":"DESKTOP-ABC","source":"lhm-lib","pcName":"DESKTOP-ABC"}

 {"type":"status","timestamp":1754150000,
  "cpu":{"name":"...","usagePct":34.5,"tempC":61.2,"clockMhz":5100,"powerW":125,"loads":[12,45,33]},
  "gpu":{"name":"...","usagePct":78.3,"tempC":71.4,"hotspotC":84.1,"vramUsedMb":6112,"vramTotalMb":12288,
         "coreClockMhz":2745,"memClockMhz":10500,"powerW":182},
  "igpu":{"name":"Intel(R) UHD Graphics","usagePct":5.2,"tempC":null,"hotspotC":null,
          "vramUsedMb":null,"vramTotalMb":null,"coreClockMhz":300,"memClockMhz":null,
          "powerW":null},
  "ram":{"usedGb":11.2,"totalGb":32,"usagePct":35,"clockMhz":3600},
  "disk":{"usagePct":40.3,"readMbPerSec":142.7,"writeMbPerSec":18.3},
  "net":{"downloadMbPerSec":8.1,"uploadMbPerSec":1.6},
  "fans":[{"label":"CPU Fan","rpm":2150},{"label":"GPU Fan","rpm":1900}],
  "fps":{"name":"Counter-Strike 2","current":142.3,"avg":138.7,"onePercentLow":97}}
  "disk":{"usagePct":87.1,"readMbPerSec":124.5,"writeMbPerSec":82.0},
  "net":{"downloadMbPerSec":21.4,"uploadMbPerSec":3.2},
  "fans":[{"label":"CPU Fan","rpm":1350},{"label":"Case Fan","rpm":920}],
  "fps":{"name":"game.exe","current":112.4,"avg":108.7,"onePercentLow":74.3}}
 ```

Missing sensors arrive as `null` (the UI shows "—"). Disk/Network/Fans/FPS fields are `null` (and their dashboard cards are hidden) when the server reports an old payload without them; this keeps the phone app compatible with older server builds. If the server cannot reach the hardware it broadcasts `"available": false` plus an `error`.

### Troubleshooting

| Problem | Solution |
|---|---|
| "Bağlantı yok" (no connection) | Make sure both devices are on the same Wi-Fi; open port 8765 (TCP) in the Windows firewall. |
| `available:false` (EXE) | Check `dist\pchw.log`; verify the DLLs in `server\vendor` are intact. |
| `available:false` (`--source http`) | Is LHM running with Remote Web Server enabled? Is `--lhm-url` correct? |
| CPU temperature "—" | Run the EXE as administrator (required by the manifest). |
| Unknown IP | Run `ipconfig` (Windows) / `ip a` (Linux) on the PC. |

### Notes

- **FPS card:** real-time frame capture via embedded `PresentMon64.exe` (built by `build_exe.bat`). The card shows current FPS, 30s average and 1% low (P99 frame time); tap a card for the min/avg/max summary. `--fps-process <name>` targets a game, or leave it empty to auto-follow the active fullscreen process. FPS is `null` if PresentMon is missing.
- **Disk / Net / Fan:** read with `psutil` on the server; cards are hidden for old server payloads where the fields are `null`.
- History data is stored in a Room DB on the device for 1 hour and cleaned automatically.

---


## Türkçe

### Özellikler

- **Android** (Kotlin, Jetpack Compose, Material 3):
   - CPU / GPU / iGPU / RAM kartları — sıcaklık, kullanım, saat hızları, güç, VRAM/RAM, çekirdek yükleri
   - Disk, Ağ, Fan ve FPS kartları — disk kullanımı ve aktarım, ağ gönderim/alan, fan RPM, oyun FPS'yi ve 1% düşük değeri (1% low)
   - Canlı grafikler, grafik penceresi (30s / 60s / 300s), 1 saatlik geçmiş (Room DB), özel logo
   - **5 renk paleti** (Default, Ocean, Ember, Forest, Black & Gold) — açık/koyu varyantlarla, ayarlardan anında uygulanır
   - **Dashboard düzenleme modu**: kartları yeniden sırala, gizle/göster, ilk ekranda sabitle (pin) ve her kartı yarım/tam genişlik arasında değiştir
   - **Yatay modda (landscape) kompakt, kaydırmasız ızgara** — nav bar otomatik gizlenir, dokununca tekrar görünür; tabletlerde daha geniş çok sütunlu düzen
   - 14 dil (ayarlardan seçilebilir)
- **Windows EXE** (tek dosya): Gömülü `LibreHardwareMonitorLib.dll` ile sıfır kurulum veri okuma; FastAPI + WebSocket ile telefona yayın. PC yoksa `--simulate` modu sahte ama gerçekçi veri üretir. Disk/Ağ sensörleri `psutil` ile okunur; paketli FPS desteği gömülü `PresentMon64.exe`'e dayanır (bunu `build_exe.bat` inşa eder).

### Mimari

```
Windows PC
  PcHwMonitor.exe (port 8765)
    ├─ varsayılan: gömülü LibreHardwareMonitorLib (işlem içi okuma, --uac-admin manifest)
    └─ veya: LibreHardwareMonitor Remote Web Server (8085) → --source http
        │ WebSocket ws://<pc-ip>:8765/ws  (her 1 sn status mesajı)
        │ CPU/GPU/RAM/igpu LHM'den; Disk/Ağ psutil'den; FPS PresentMon64'ten
Android uygulaması (aynı Wi-Fi)
```

Android yalnızca sunucuyla konuşur; LibreHardwareMonitor ile doğrudan teması yoktur.

### Windows EXE (önerilen yol)

1. `dist\PcHwMonitor.exe`'yi çalıştır, UAC istemini onayla (CPU sıcaklığı için yönetici gerekir).
2. Telefonda **Ayarlar** sekmesinden bilgisayarın Wi-Fi IP'sini (örn. `192.168.1.50`) ve portu (`8765`) gir, **Kaydet**'e bas. Dashboard otomatik bağlanır.
3. EXE sistem tepsisinde (saatin yanı) simge olarak durur; sağ tık → **Kapat** ile sunucu kapanır. İşlem yokken telefonda "Bağlantı yok" görünür.

Yeniden derlemek için Windows'ta proje kökünde `build_exe.bat` (pythonnet + pyinstaller gerektirir; DLL'leri `server\vendor` içine kopyalar ve paketler). Paketli modda hata logu `dist\pchw.log` dosyasına yazılır.

`psutil` (Disk ve Ağ sensörleri için) `server/requirements.txt`'de listelenir. Paketli FPS desteği ayrıca `server/presentmon/PresentMon64.exe` gerektirir; `build_exe.bat` bulursa `dist/` içine kopyalar, yoksa FPS devre dışı kalır.

### Sunucu Kurulumu (geliştirme)

**Windows (gerçek veri):**

```bash
cd server
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt -r requirements-dev.txt   # pythonnet için requirements-dev
.venv\Scripts\python main.py
```

Seçenekler: `--port <8765>` · `--source <auto|http|lib>` · `--lhm-url <http://127.0.0.1:8085/data.json>` · `--interval <ms>` · `--simulate` · `--fps-process <name>`

- `--source auto` (varsayılan): gömülü DLL varsa `lib`, yoksa LHM web sunucusuna düşer.
- `--source lib`: `LibreHardwareMonitorLib.dll`'yi süreç içinde okur (DLL yolu `LHMDIR` env değişkeniyle verilebilir).
- `--source http`: harici LibreHardwareMonitor'un JSON API'sini okur (Options → Remote Web Server, port 8085).
- `--fps-process <name>`: kare yakalama için belirli bir sürece hedefler (örn. `--fps-process game.exe`). Boş bırakılırsa veya verilmezse PresentMon aktif tam ekran süreceyi takip eder. `server/presentmon/PresentMon64.exe` gerektirir; eksikse FPS devre dışıdır ve `fps` `null` gelir.

**Simülasyon modu (test için, herhangi bir OS):**

```bash
cd server
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python main.py --simulate --port 8765
```

**Sunucuyu doğrula:**

```bash
curl http://localhost:8765/health          # {"ok":true,"source":"..."}
.venv/bin/python smoke_test.py             # welcome + 3 status mesajı doğrular
.venv/bin/python -m pytest tests -v        # 37 test
```

### Android Kurulumu

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Testler: `./gradlew :app:testDebugUnitTest`

### WebSocket Protokolü

Sunucu → istemci, her saniye tek `status` mesajı; bağlantıda önce `welcome`:

```json
{"type":"welcome","intervalMs":1000,"serverName":"DESKTOP-ABC","source":"lhm-lib","pcName":"DESKTOP-ABC"}

 {"type":"status","timestamp":1754150000,
  "cpu":{"name":"...","usagePct":34.5,"tempC":61.2,"clockMhz":5100,"powerW":125,"loads":[12,45,33]},
  "gpu":{"name":"...","usagePct":78.3,"tempC":71.4,"hotspotC":84.1,"vramUsedMb":6112,"vramTotalMb":12288,
         "coreClockMhz":2745,"memClockMhz":10500,"powerW":182},
  "igpu":{"name":"Intel(R) UHD Graphics","usagePct":5.2,"tempC":null,"hotspotC":null,
          "vramUsedMb":null,"vramTotalMb":null,"coreClockMhz":300,"memClockMhz":null,
          "powerW":null},
  "ram":{"usedGb":11.2,"totalGb":32,"usagePct":35,"clockMhz":3600},
  "disk":{"usagePct":40.3,"readMbPerSec":142.7,"writeMbPerSec":18.3},
  "net":{"downloadMbPerSec":8.1,"uploadMbPerSec":1.6},
  "fans":[{"label":"CPU Fan","rpm":2150},{"label":"GPU Fan","rpm":1900}],
  "fps":{"name":"Counter-Strike 2","current":142.3,"avg":138.7,"onePercentLow":97}}
  "disk":{"usagePct":87.1,"readMbPerSec":124.5,"writeMbPerSec":82.0},
  "net":{"downloadMbPerSec":21.4,"uploadMbPerSec":3.2},
  "fans":[{"label":"CPU Fan","rpm":1350},{"label":"Case Fan","rpm":920}],
  "fps":{"name":"game.exe","current":112.4,"avg":108.7,"onePercentLow":74.3}}
 ```

Eksik sensörler `null` gelir (UI "—" gösterir). Disk/Ağ/Fan/FPS alanları eski sunucu payloadlarında `null` gelirse (ve o kartlar gizlenir); telefon uygulaması bu nedeniyle eski sunucu sürümleriyle uyumludur. Sunucu donanıma ulaşamazsa `"available": false` + `error` yayınlar.

### Sorun Giderme

| Sorun | Çözüm |
|---|---|
| "Bağlantı yok" | Aynı Wi-Fi ağında olduğunuzdan emin olun; Windows güvenlik duvarında 8765 portunu açın (TCP). |
| `available:false` (EXE) | `dist\pchw.log` dosyasına bakın; `server\vendor` klasöründeki DLL'ler sağlam mı kontrol edin. |
| `available:false` (`--source http`) | LHM çalışıyor ve Remote Web Server açık mı? `--lhm-url` doğru mu? |
| CPU sıcaklığı "—" | EXE'yi yönetici olarak çalıştırın (manifest gerektirir). |
| IP bilinmiyor | Bilgisayarda `ipconfig` (Windows) / `ip a` (Linux) çalıştırın. |

### Notlar

- **FPS kartı:** gömülü `PresentMon64.exe` ile (build_exe.bat inşa eder). Kart anlık FPS, 30s ortalama ve 1% düşük (1% low) değerini gösterir; min/avg/max özet için karta dokunun. `--fps-process <name>` bir oyuna hedefler, boş bırakılırsa aktif tam ekran süreceyi takip eder. PresentMon yoksa FPS `null`'dir.
- **Disk / Ağ / Fan:** sunucuda `psutil` ile okunur; eski sunucu payloadlarında bu alanlar `null` ise kartlar gizlenir.
- Geçmiş verileri cihazda Room DB'de 1 saat saklanır, otomatik temizlenir.

---


## Lisans / License

[GNU AGPL v3](LICENSE) (GNU Affero General Public License, version 3).

- The Windows EXE bundles [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) (`LibreHardwareMonitorLib.dll`), also AGPL-3.0. When distributing the EXE, the corresponding source and license must be made available (see section 13 of the AGPL).
- The Android app and server source code are licensed under AGPL-3.0.
