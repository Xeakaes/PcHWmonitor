# v1.4 — Dashboard Layout & Theme Redesign — Design Spec

Tarih: 2026-08-07
Durum: Onaylandı

## Amaç

v1.4, telefon yatay modda önemli metriklerin kaydırmasız görünmesini, tablet/ekran boyutlarına uyum sağlamayı, kullanıcıya kart gizle/sırala yetkisi vermeyi ve hazır renk paletleriyle tema çeşitliliğini hedefler.

## Kullanıcı Kararları (toplanan)

- Dashboard düzen güncellemeleri odak.
- Kart özelleştirme: karta üstü menü + düzenleme modu (ayarlar ekranı değil).
- Telefon yatay: önemli metrikler ilk ekranda, gerisi kaydırmalı.
- Tablet/geniş ekran: genişliğe göre adaptive grid.
- Tema: hazır paletler (3-4 → 5 seçenek), kullanıcı tanımlı değil.
- Önemli metrikler: varsayılan sabit + kullanıcı işaretleyebilir.
- Yatay yerleşim: pinned blok ilk satırlar(ler), gerisi altında.
- Yeni istek: siyah-altın temalı da ekle.

## Mimari Genel Bakış

**Çekirdek kavram: `DashboardLayout`** (kullanıcı düzeni)

- `LayoutEntry(cardId, visible, priority)` listesi.
- Kart kimlikleri: `cpu`, `gpu`, `igpu`, `fps`, `ram`, `disk`, `net`, `fan`.
- `priority` iki kademe: `PINNED` (yatay ilk ekran garantisi) ve `NORMAL`.
- DataStore'da JSON string olarak saklanır; yoksa varsayılan düzen.
- Varsayılan: tüm kartlar görünür, `cpu/gpu/fps/ram` pinned.

### Bileşenler

#### Kart üstü menü
- `MetricCard` başlığına `⋮` butonu (yalnızca dashboard, normal mod).
- Menü içeriği: **Gizle**, **Önemli/Ekonomik** (priority toggle).
- `igpu/disk/net/fan` non-pinned varsayılan; menüde pinned işaretlenebilir.

#### Düzenleme Modu
- Dashboard başlık çubuğunda kalem ikonu.
- `EditMode`: kartlar `reorderable` list / grid, gizli kartlar alt `Gizli Kartlar` şeridinden sürüklenerek geri konabilir.
- `Bitti` → layout `DataStore`'a yaz; `İptal` → değişiklik yok.
- Sadece görünür kartlar yeniden sıralanır; gizli kartlar orderı korunur.

### Düzen Motoru (`LayoutHelper` genişletilir)

Girdi: `DashboardLayout` + ekran boyutu (maxWidth/maxHeight) + yön.

Çıktı: render edilecek kart dizisi/grid tanımı.

- **Telefon portre**: 1 sütun, sıralı akış (mevcut davranış).
- **Telefon yatay**: `GridCells.Adaptive(minWidth)` ile sütun sayısı. `pinned` kartlar ilk satır(ler) dolur (ör. 2 kolon → ilk satır CPU+GPU, ikinci FPS+RAM); kalan görünür kartlar alta devam, kaydırmayla ulaşılır.
- **Tablet/büyük**: adaptive grid (2 → 4 sütun), hem port hem yatay.

#### Veri akışı

- `SettingsStore` → `prefs.dashboardLayout: String`.
- `MonitorViewModel`: `dashboardLayout: StateFlow<DashboardLayout>` + `updateDashboardLayout(layout)`.
- `DashboardLayout` yön/ekran yok bilmez; motor yalnızca okur.

### Tema

- `Palette` nesneleri: her biri `lightColorScheme()` + `darkColorScheme()`.
- Paletler: `default` (mevcut mor family), `ocean` (mavi), `ember` (turuncu-kırmızı), `forest` (yeşil), `gold` (siyah + altın sarısı).
- `SettingsStore.themePalette: String` (default|ocean|ember|forest|gold).
- Mevcut renk sabitleri (TempGreen/Yellow/Orange/Red, ChartBlue, GaugeTrack...) her palette alt tonlarla eşleştirilir; `TemperatureColor` hesabı palet'ten türetilebilir.
- Altın temada uyarıcı sarı tonları (sıcaklık warning) altın vurgusal renkle çakışmayacak şekilde ayırt edilir (örn. derin kehribar).
- `SettingsScreen`: tema seçim listesi mevcut theme radio deseni ile (`theme_default`, `theme_ocean`, ... `string`).

## Hata / Durum Yönetimi

- Bozuk DataStore JSON: varsayılana geri düş, sessiz.
- Bilinmeyen `cardId` (eski veri): dashboard render'da yok sayar; `Bitti`te düzeni temizler.
- Veri yok (disk/fps/fan null): kart render edilmez; layout kaydında tutulur.
- Layout değişiklikleri `MonitorController`'a dokunmaz.

## Testler

- `DashboardLayout` serialize/deserialize round-trip; bozuk JSON; bilinmeyen kartlar.
- Düzen motoru: portre 1 sütun; yatay pinned blok + kalan grid; adaptive sütun sayısı.
- Palette: her palette light+dark üretimi; contrast (onSurface vs surface).
- Mevcut testleri bozmaz (31 unit + server 37).

## Kapsam Dışı

- Sürekleme animasyonları (basit sıralama yeter), custom sürükleme kütüphanesi.
- Kullanıcı tanımlı renk seçici; Dynamic Color (Material You).
- History / Settings ekranlarının görsel yenilenmesi (yalnız Dashboard + tema kapsamı).
- Serbest renk tekeri, renk düzenleyici.