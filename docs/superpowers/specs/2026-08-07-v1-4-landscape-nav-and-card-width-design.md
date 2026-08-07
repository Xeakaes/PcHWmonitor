# v1.4 Bugfix: Landscape Fullscreen & Kart Genişliği — Tasarım

Tarih: 2026-08-07 · Sürüm: v1.4 (bugfix — **version bump yok**; `app/build.gradle.kts` dokunulmaz)

## Problem

1. Telefon yatay modundayken alttaki `NavigationBar` (Dashboard / History / Settings) neredeyse bir tam satırı kapatıyor.
2. Yatay modda kart genişliği sabit: CPU/GPU gibi kartlar satırı ikiye bölüyor ama RAM gibi kartlar bir satırı tamamen kaplıyor. Kullanıcı bir kartın satırın tamamını mı yoksa yarısını mı kaplayacağını ayarlayamıyor.

## Amaç

- Yatay modda alt navigasyon çubuğunu otomatik gizleyip, dokununca geçici olarak geri gösteren bir mekanizma (YouTube tarzı).
- Kullanıcının her kartı "yarım genişlik" veya "tam genişlik" olarak işaretleyebilmesi; etki yatay modda görünür.

## Kararlar (kullanıcı onaylı)

1. **Nav bar gizleme:** Yatay modda tüm sekmelerde otomatik gizlenir. Ekrana dokununca geçici geri gelir, tekrar dokununca gizlenir. Dikey moda dönünce her zaman geri gelir.
2. **Nav bar başlangıcı:** Yatay moda geçince gizli başlar; ekranın altında ortada yarı saydam küçük bir "göster" butonu (overlay) görünür.
3. **Kart genişliği:** İki durumlu — yarım (satırda 2 kart yan yana) veya tam (satırı tek başına kaplar). Ayar, edit modu ve her kart menüsünden (kebab) yapılır.
4. **Satır paketleme:** Tam kart kendi satırını açar; yarım kartlar boş kalan yarıyı doldurur. Kart sıralaması korunur.
5. **Varsayılan davranış:** Tüm kartlar `wide = false` (yarım). Tablet grid kuralı (2/3/4 sütun) değişmez; tablet'te `wide` kart tam satır kaplar, normal kartlar mevcut grid hücresi olarak kalır.
6. **Dikey mod:** Genişlik ayarı dikeyde etkisizdir — dikey her zaman tam genişlik gösterir (1 sütun).

## Bileşenler

### 1. Veri modeli — `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardLayout.kt`

- `LayoutEntry`'ye `val wide: Boolean = false` alanı eklenir.
- `LayoutEntryDto`'ya `val wide: Boolean = false` alanı eklenir (internal, `@Serializable`).
- `toDto()` / `fromDto()` `wide` alanını taşır.
- Eski JSON (v1.4'ten kalma, `wide` alanı yok) → serileştirme default değeri kullanır; mevcut kullanıcı ayarları bozulmaz (toleranlı `Json` zaten mevcut).

### 2. Kart menüsü — `ui/dashboard/CardMenu.kt`

- `CardMenuAction`'a genişlik eylemi eklenir (örn. `SET_WIDE(wide: Boolean)` veya ayrı `CELL` / `WIDE`).
- `applyLayoutAction` genişlik değişikliğini uygular.

### 3. Satır paketleme — `ui/dashboard/LayoutHelper.kt`

- Mevcut `RenderPlan` + `buildRows` akışı `wide` alanını kullanacak şekilde güncellenir:
  - **Telefon yatay (2 sütun):** `wide` kart kendi satırına; yarım kartlar 2'li paketlenir; boş yarım kalan yer doldurulur.
  - **Tablet (2/3/4 sütun):** `wide` kart tam satır; normal kartlar mevcut `columnCount(size, maxWidth)` grid'inde kalır.
  - **Dikey telefon:** sabit 1 sütun, `wide` uygulanmaz.
- Sıralama korunur (pinned first-screen mantığı aynen).

### 4. Dashboard — `ui/dashboard/DashboardScreen.kt`

- **Edit modu:** `CardEditControls`'a "tam genişlik / yarım genişlik" toggle ikonu eklenir.
- **Kart menüsü:** kebab menüsüne "Yarım genişlik" / "Tam genişlik" öğesi (kartın mevcut durumuna göre uygun olanı).
- Yeni string parametreleri: `labelCardWidthHalf`, `labelCardWidthFull`.

### 5. Nav bar gizleme — `ui/navigation/AppNavHost.kt`

- Landscape algılama (`BoxWithConstraints` veya `LocalConfiguration`).
- Yatay + gizli: `NavigationBar` render edilmez; alt-ortada yarı saydam "göster" butonu (chevron-up ikonu) overlay olarak gösterilir; dokununca `NavigationBar` geçici görünür, tekrar dokununca gizlenir.
- Tüm sekmelerde (dashboard/history/settings) aynı davranış.
- Durum `rememberSaveable` ile tutulur (rotasyonda korunur).
- Dikey modda `NavigationBar` her zaman görünür.

### 6. Stringer — `app/src/main/res/values/strings.xml` + `values-tr/strings.xml`

Yeniler: `card_width_half` (Half width / Yarım genişlik), `card_width_full` (Full width / Tam genişlik), `nav_show` (Show navigation / Çubuğu göster, contentDescription olarak).

### 7. Testler

- `LayoutHelperTest`: yarım/tam paketleme (telefon yatay + tablet), sıra korunumu, pinned first-screen, dikey davranış.
- `DashboardLayoutTest`: `wide` serileştirme + eski JSON (wide alanı yok) uyumluluğu.
- `CardMenuTest`: genişlik toggle eylemi.

## Sürüm Notu

- `versionCode`/`versionName` DEĞİŞMEZ (1.4 bugfix).
- Yeni APK aynı 1.4 sürüm numarasıyla üretilir; istenirse mevcut v1.4 release'ine asset olarak eklenir.

## Kapsam Dışı

- Dikey modda yarım kartlar.
- Drag-and-drop yeniden boyutlandırma (yalnız 2 durum: yarım/tam).
- Nav bar konumunun kullanıcı tarafından taşınması.