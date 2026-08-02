# PC HW Monitor

Bilgisayarının anlık sistem verilerini yerel Wi-Fi üzerinden telefonunda gösteren modern dashboard.
A modern dashboard that shows your PC's real-time hardware stats on your phone over local Wi-Fi.

**Lisansa / License:** [AGPL-3.0](LICENSE) — EXE, AGPL-3.0 lisanslı [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) DLL'i paketler.

---

## Türkçe

### Özellikler

- **Android** (Kotlin, Jetpack Compose, Material 3):
  - CPU / GPU / iGPU / RAM kartları — sıcaklık, kullanım, saat hızları, güç, VRAM/RAM, çekirdek yükleri
  - Canlı grafikler, 1 saatlik geçmiş (Room DB), açık/koyu tema, özel logo
  - **Yatay modda (landscape) tüm kartlar tek ekranda kompakt 2×2 ızgara** — kaydırmasız
  - 14 dil (ayarlardan seçilebilir)
- **Windows EXE** (tek dosya): Gömülü `LibreHardwareMonitorLib.dll` ile sıfır kurulum veri okuma; FastAPI + WebSocket ile telefona yayın. PC yoksa `--simulate` modu sahte ama gerçekçi veri üretir.

### Mimari

```
Windows PC
  PcHwMonitor.exe (port 8765)
    ├─ varsayılan: gömülü LibreHardwareMonitorLib (işlem içi okuma, --uac-admin manifest)
    └─ veya: LibreHardwareMonitor Remote Web Server (8085) → --source http
        │ WebSocket ws://<pc-ip>:8765/ws  (her 1 sn status mesajı)
Android uygulaması (aynı Wi-Fi)
```

Android yalnızca sunucuyla konuşur; LibreHardwareMonitor ile doğrudan teması yoktur.

### Windows EXE (önerilen yol)

1. `dist\PcHwMonitor.exe`'yi çalıştır, UAC istemini onayla (CPU sıcaklığı için yönetici gerekir).
2. Telefonda **Ayarlar** sekmesinden bilgisayarın Wi-Fi IP'sini (örn. `192.168.1.50`) ve portu (`8765`) gir, **Kaydet**'e bas. Dashboard otomatik bağlanır.
3. EXE sistem tepsisinde (saatin yanı) simge olarak durur; sağ tık → **Kapat** ile sunucu kapanır. İşlem yokken telefonda "Bağlantı yok" görünür.

Yeniden derlemek için Windows'ta proje kökünde `build_exe.bat` (pythonnet + pyinstaller gerektirir; DLL'leri `server\vendor` içine kopyalar ve paketler). Paketli modda hata logu `dist\pchw.log` dosyasına yazılır.

### Sunucu Kurulumu (geliştirme)

**Windows (gerçek veri):**

```bash
cd server
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt -r requirements-dev.txt   # pythonnet için requirements-dev
.venv\Scripts\python main.py
```

Seçenekler: `--port <8765>` · `--source <auto|http|lib>` · `--lhm-url <http://127.0.0.1:8085/data.json>` · `--interval <ms>` · `--simulate`

- `--source auto` (varsayılan): gömülü DLL varsa `lib`, yoksa LHM web sunucusuna düşer.
- `--source lib`: `LibreHardwareMonitorLib.dll`'yi süreç içinde okur (DLL yolu `LHMDIR` env değişkeniyle verilebilir).
- `--source http`: harici LibreHardwareMonitor'un JSON API'sini okur (Options → Remote Web Server, port 8085).

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
.venv/bin/python -m pytest tests -v        # 20 test
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
        "coreClockMhz":2745,"memClockMhz":10500,"powerW":182,"fps":null},
 "igpu":{"name":"Intel(R) UHD Graphics","usagePct":5.2,"tempC":null,"hotspotC":null,
         "vramUsedMb":null,"vramTotalMb":null,"coreClockMhz":300,"memClockMhz":null,
         "powerW":null,"fps":null},
 "ram":{"usedGb":11.2,"totalGb":32,"usagePct":35,"clockMhz":3600}}
```

Eksik sensörler `null` gelir (UI "—" gösterir). Sunucu donanıma ulaşamazsa `"available": false` + `error` yayınlar.

### Sorun Giderme

| Sorun | Çözüm |
|---|---|
| "Bağlantı yok" | Aynı Wi-Fi ağında olduğunuzdan emin olun; Windows güvenlik duvarında 8765 portunu açın (TCP). |
| `available:false` (EXE) | `dist\pchw.log` dosyasına bakın; `server\vendor` klasöründeki DLL'ler sağlam mı kontrol edin. |
| `available:false` (`--source http`) | LHM çalışıyor ve Remote Web Server açık mı? `--lhm-url` doğru mu? |
| CPU sıcaklığı "—" | EXE'yi yönetici olarak çalıştırın (manifest gerektirir). |
| IP bilinmiyor | Bilgisayarda `ipconfig` (Windows) / `ip a` (Linux) çalıştırın. |

### Notlar

- İlk sürümde FPS alanı protokolde hazır ama `null`; gelecekte MSI Afterburner üzerinden doldurulabilir.
- Geçmiş verileri cihazda Room DB'de 1 saat saklanır, otomatik temizlenir.

---

## English

### Features

- **Android** (Kotlin, Jetpack Compose, Material 3):
  - CPU / GPU / iGPU / RAM cards — temperatures, usage, clock speeds, power, VRAM/RAM, core loads
  - Live charts, 1-hour history (Room DB), light/dark theme, custom logo
  - **Landscape mode: all cards in a compact scroll-free 2×2 grid on one screen**
  - 14 languages (selectable in Settings)
- **Windows EXE** (single file): zero-install hardware reading via an embedded `LibreHardwareMonitorLib.dll`; FastAPI + WebSocket streams data to your phone. No PC hardware? `--simulate` mode generates realistic fake data.

### Architecture

```
Windows PC
  PcHwMonitor.exe (port 8765)
    ├─ default: embedded LibreHardwareMonitorLib (in-process reading, --uac-admin manifest)
    └─ or: LibreHardwareMonitor Remote Web Server (8085) → --source http
        │ WebSocket ws://<pc-ip>:8765/ws  (one status message every second)
Android app (same Wi-Fi)
```

The Android app only talks to the server; it never touches LibreHardwareMonitor directly.

### Windows EXE (recommended path)

1. Run `dist\PcHwMonitor.exe`, accept the UAC prompt (admin rights are needed for CPU temperature).
2. On your phone, open the **Settings** tab, enter the PC's Wi-Fi IP (e.g. `192.168.1.50`) and port (`8765`), press **Save**. The dashboard connects automatically.
3. The EXE sits in the system tray (next to the clock); right-click → **Kapat** (Close) shuts the server down. The phone shows "Bağlantı yok" (No connection) when it is offline.

To rebuild, run `build_exe.bat` at the project root on Windows (needs pythonnet + pyinstaller; it copies the DLLs into `server\vendor` and bundles them). In packaged mode, errors are logged to `dist\pchw.log`.

### Server Setup (development)

**Windows (real data):**

```bash
cd server
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt -r requirements-dev.txt   # pythonnet via requirements-dev
.venv\Scripts\python main.py
```

Options: `--port <8765>` · `--source <auto|http|lib>` · `--lhm-url <http://127.0.0.1:8085/data.json>` · `--interval <ms>` · `--simulate`

- `--source auto` (default): uses the embedded DLL if present (`lib`), falls back to the LHM web server.
- `--source lib`: reads `LibreHardwareMonitorLib.dll` in-process (DLL path can be set via the `LHMDIR` env var).
- `--source http`: reads the JSON API of an external LibreHardwareMonitor (Options → Remote Web Server, port 8085).

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
.venv/bin/python -m pytest tests -v        # 20 tests
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
        "coreClockMhz":2745,"memClockMhz":10500,"powerW":182,"fps":null},
 "igpu":{"name":"Intel(R) UHD Graphics","usagePct":5.2,"tempC":null,"hotspotC":null,
         "vramUsedMb":null,"vramTotalMb":null,"coreClockMhz":300,"memClockMhz":null,
         "powerW":null,"fps":null},
 "ram":{"usedGb":11.2,"totalGb":32,"usagePct":35,"clockMhz":3600}}
```

Missing sensors arrive as `null` (the UI shows "—"). If the server cannot reach the hardware it broadcasts `"available": false` plus an `error`.

### Troubleshooting

| Problem | Solution |
|---|---|
| "Bağlantı yok" (no connection) | Make sure both devices are on the same Wi-Fi; open port 8765 (TCP) in the Windows firewall. |
| `available:false` (EXE) | Check `dist\pchw.log`; verify the DLLs in `server\vendor` are intact. |
| `available:false` (`--source http`) | Is LHM running with Remote Web Server enabled? Is `--lhm-url` correct? |
| CPU temperature "—" | Run the EXE as administrator (required by the manifest). |
| Unknown IP | Run `ipconfig` (Windows) / `ip a` (Linux) on the PC. |

### Notes

- The FPS field exists in the protocol but is `null` for now; it may be filled via MSI Afterburner in the future.
- History data is stored in a Room DB on the device for 1 hour and cleaned automatically.

---

## Lisans / License

[GNU AGPL v3](LICENSE) (GNU Affero General Public License, version 3).

- The Windows EXE bundles [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) (`LibreHardwareMonitorLib.dll`), also AGPL-3.0. When distributing the EXE, the corresponding source and license must be made available (see section 13 of the AGPL).
- The Android app and server source code are licensed under AGPL-3.0.
