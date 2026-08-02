# PC HW Monitor v2: Sıfır-kurulum EXE, iGPU, Dil Seçici, Logo

Tarih: 2026-08-02 · Durum: Onaylandı (kullanıcı tasarımı onayladı)

## 1. Amaç

Kullanıcının onayladığı dört özellik:

1. **PC tarafı tek EXE, sıfır kurulum** — kullanıcı LibreHardwareMonitor'u (LHM) kurup yapılandırmak zorunda kalmadan veri okunur; EXE tek dosyadır, konsol penceresi açmaz.
2. **Intel UHD Graphics (iGPU) verisi** — Dashboard'da canlı kart (geçmiş verisine girmez).
3. **Dil seçici** — Ayarlar'da "Sistem (varsayılan)" + 13 dil; anında uygulanır.
4. **Logo** — Varsayılan simge yerine uygulamaya özel adaptif simge.

Bağlam: Kullanıcı, LHM'nin zaten veriyi gösterdiğini ancak kullanıcının bununla uğraşmasını istemediğini belirtti; sunucu tepsiden/terminalden görülebilir ve kapatılabilir olabilir. Tamamen gizli (kullanıcıdan saklı) toplama istenmedi ve inşa edilmedi.

## 2. Mimari değişiklikler

```
Windows PC (tek EXE: PcHwMonitor.exe)
  pythonnet + LibreHardwareMonitorLib.dll  →  sensörleri işlem içinde okur
        │  (LHM işlemi yok, kurulum yok, yapılandırma yok)
  WebSocket yayını (port 8765, her 1 sn)
Android uygulaması (aynı Wi-Fi)
```

HTTP tabanlı eski akış (kullanıcı LHM çalıştırıyorsa) geriye dönük uyumlu olarak korunur.

## 3. Bileşenler

### 3.1 Sunucu: gömülü LHM okuma (`adapters/lhm_lib.py`)

- pythonnet (>=3.1.0, Python 3.14 desteği) ile `LibreHardwareMonitorLib.dll` yüklenir.
- DLL hedefi .NET Framework 4.7.2 → Windows 10/11'de hazır .NET Framework 4.8 ile çalışır, runtime kurulumu gerekmez.
- `Computer` nesnesi: `IsCpuEnabled=True, IsGpuEnabled=True, IsMemoryEnabled=True`; `Open()`.
- Her tick'te tüm donanım + alt donanım `Update()` çağrılır; sensörler sözlüğe çevrilip **mevcut eşleştirme fonksiyonları** (`adapters/lhm.py` içindeki `_find/_loads/_clock_max/_num`) ile aynı şemaya dönüştürülür.
- Hata durumları `available:false + error` üretir (LHM HTTP adaptörüyle aynı sözleşme).
- `HardwareType` enum değerleri: `Cpu`, `GpuNvidia`, `GpuAmd`, `GpuIntel`, `Memory`.
- Test: Linux/WSL'de .NET çalıştırılamaz → bu adaptör için birim test yok; Windows'ta çalışma anında doğrulanır. Mantık yüzeyi ince tutulur.

### 3.2 Sunucu: `main.py` kaynak seçimi

- Yeni bayrak: `--source {auto,http,lib}` (varsayılan `auto`).
  - `auto`: `lhm_lib` yüklenebiliyorsa → lib; değilse → http (eski davranış).
  - `http`: mevcut LHM HTTP adaptörü (testler ve fallback için).
  - `lib`: gömülü okuma (DLL yoksa `available:false`).
- `--simulate`, `--port`, `--interval`, `--lhm-url` korunur.
- `welcome.source`: `"lhm-lib"` (lib) / `"librehardwaremonitor"` (http) / `"simulator"`.

### 3.3 Sunucu: iGPU (`igpu` alanı)

- `schema.py`: `StatusMessage`'a `igpu: GpuInfo | None = None` eklenir.
- HTTP adaptörü: `gpu` = Nvidia/AMD (mevcut), `igpu` = `GpuIntel` / `/gpu-intel*`.
- Simülatör: `igpu` üretir (örn. "Intel UHD Graphics", düşük değerler).
- LHM'nin Intel iGPU'da sıcaklık vermediği biliniyor (kullanıcının makinesinde sıcaklık grubu yok) → ilgili alanlar `null` kalır, UI "—" gösterir.
- Geçmiş (Room DB) değişmez — yalnız canlı kart.

### 3.4 Android: iGPU kartı

- Model + `StatusParser`: `igpu` anahtarı ayrıştırılır (aynı GpuInfo biçimi), yoksa `null`.
- `MonitorController`: `igpu` durumu yayınlanır.
- Dashboard: `igpu != null` ise mevcut GpuCard yeniden kullanılarak ikinci kart gösterilir.
- Yeni string anahtarı: `label_integrated_gpu` (13 dilde).

### 3.5 Android: dil seçici

- `SettingsStore`: `language: String?` (DataStore), `null` = sistem.
- `MainActivity`: başlangıçta kayıtlı dil varsa `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))` ile uygulanır (anında, yeniden başlatma yok). `androidx.appcompat:appcompat` bağımlılığı yoksa eklenir.
- `SettingsScreen`: "Sistem (varsayılan)" + 13 dil listeli açılır menü (her dil kendi dilinde yazılır). Kaydet'e basınca kaydedilir ve uygulanır.
- Dil etiketleri (DataStore'da saklanan BCP-47 biçimli kodlar, `values-*` klasörleriyle eşleşir): `fr, de, es, it, pt, pt-BR, ru, tr, pl, nl, zh, zh-TW, ja` — `null` = sistem.
- Yeni string anahtarları (13 dilde): `settings_language`, `settings_language_system`, 13 dil adı.
- Mevcut key-eşitlik kontrolü tüm locale'lerde yeniden çalıştırılır.

### 3.6 Android: logo

- `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`: koyu lacivert zemin + termometre/gösterge vektör ön planı (`drawable/ic_launcher_foreground.xml`).
- Eski PNG'ler (API 24–25): PIL ile 5 yoğunlukta (48/72/96/144/192) üretilir; webp dosyalarının yerini alır.
- Manifest değişmez (`@mipmap/ic_launcher`).

### 3.7 EXE paketleme (Windows'ta, kullanıcının makinesi)

- `requirements-dev.txt`: `pythonnet`, `pyinstaller` eklenir.
- DLL'ler EXE'ye gömülür: `--add-data "LibreHardwareMonitorLib.dll;."` (+ gerekiyorsa bağımlılıklar; önce en küçük set denenir, yükleme hatası olursa genişletilir — `C:\Users\msi\LibreHardwareMonitor` kaynak).
- PyInstaller: `--onefile --noconsole --name PcHwMonitor`, `server/` içinden.
- Derleme ve çalışma anı doğrulaması Windows'ta cmd üzerinden yapılır (`/health` + WebSocket smoke testi).

## 4. Geriye dönük uyumluluk

- `igpu` alanı isteğe bağlıdır; eski sunucu → yeni uygulama ve tersi çalışır (alan null gelir).
- HTTP adaptörü ve eski testler aynen geçer.
- `--simulate` akışı değişmez.

## 5. Doğrulama

- Sunucu: `pytest` (mevcut 11 + yeni iGPU/schema testleri).
- Android: birim testler + `assembleDebug` (Linux ortamında).
- Windows: pythonnet + DLL yükleme smoke testi; EXE derlemesi; EXE çalışırken `/health` + WebSocket doğrulaması.
- Tüm değişiklikler Windows kopyasına rsync ile senkronlanır; Android'i kullanıcı Studio'da derler.

## 6. Notlar

- LibreHardwareMonitorLib AGPL-3.0 lisanslıdır: kişisel kullanımda sorun yok; kamuya dağıtımda projenin açık kaynak yayımlanması gerekir. README'ye tek satır not eklenir.
- README güncellenir: EXE kullanımı, iGPU, dil seçici, AGPL notu, protokolde `igpu`.

## 7. Kod yorumları kuralı (kullanıcı isteği)

- Satır satır yorum YOK. Kod, işlevine göre bölümlere ayrılır ve her bölümün başına **kısa, İngilizce** bir bölüm başlığı yorumu konur (ör. `# -- sensor lookup --`).
- Dil etiketleri zaten İngilizce/Latin BCP-47 kodlarıdır (`fr, de, es, it, pt, pt-BR, ru, tr, pl, nl, zh, zh-TW, ja`); ek çeviri gerekmez.
