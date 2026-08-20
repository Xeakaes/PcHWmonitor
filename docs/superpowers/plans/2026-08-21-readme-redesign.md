# README Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure `README.md` with a hero section (title, taglines, badges, 3-layered phone showcase), a nine-section English half, a mirrored Turkish half, and new Screenshots/Download/FAQ sections.

**Architecture:** Single-file rewrite of `README.md`. New content (hero, download, FAQ) is written verbatim in this plan; existing long-form content (protocol JSON example, architecture diagram, setup instructions) is moved verbatim from the current file at the line references given. No new image files are added — all six images already exist in `docs/images/`.

**Tech Stack:** Markdown (GitHub-flavored), minimal inline HTML for the centered hero only.

## Global Constraints

- Bilingual single file: `## English` first, `## Türkçe` second, separated by `---`.
- No emojis anywhere in the README (user preference).
- Images referenced as `docs/images/<file>.jpg`, relative to repo root (renders on GitHub).
- All existing external links and badges must be preserved: CodeQL, AlternativeTo, Website, Android Weekly, Patreon, LICENSE, LibreHardwareMonitor.
- Only the 6 images already in `docs/images/` are used: `hero_left.jpg`, `hero_right.jpg`, `main_ember.jpg`, `main_light.jpg`, `main_landscape.jpg`, `settings_light.jpg`.
- Exact section order EN: Features, Screenshots, Download, Architecture, Getting Started, WebSocket Protocol, Troubleshooting, FAQ, License. TR mirrors this order.
- No technical content may be lost or shortened vs the current README.

---

### Task 1: Hero + badges + language anchors

**Files:**
- Modify: `README.md:1-25` (replace everything from line 1 `# PC HW Monitor` through line 25 `---`)

**Interfaces:**
- Produces: Hero block ending with `---`. Two HTML anchors `<a id="english"></a>` and `<a id="turkce"></a>` placed immediately before the `## English` and `## Türkçe` headings — Task 2 and Task 3 rely on them existing.

- [ ] **Step 1: Replace the current header block**

Read `README.md` lines 1-25 (the `# PC HW Monitor` heading, the two tagline lines, the badge row, and the Patreon support lines), then replace that entire block with exactly:

```html
<div align="center">

# PC HW Monitor

**Your PC, on your phone.** Real-time CPU, GPU, RAM, disk, network, fan and FPS stats streamed to your Android device over local Wi-Fi.

Bilgisayarının anlık sistem verilerini yerel Wi-Fi üzerinden telefonunda gösteren modern dashboard.

<a href="https://github.com/Xeakaes/PcHWmonitor/actions/workflows/codeql.yml" target="_blank">
  <img src="https://github.com/Xeakaes/PcHWmonitor/actions/workflows/codeql.yml/badge.svg" alt="CodeQL Status">
</a>
<a href="https://alternativeto.net/software/pc-hw-monitor/about/" target="_blank">
  <img src="https://img.shields.io/badge/AlternativeTo-Listed-blue" alt="AlternativesTo Page">
</a>
<a href="https://xeakaes.github.io/PcHWmonitor/" target="_blank">
  <img src="https://img.shields.io/badge/Website-Visit-brightgreen" alt="Project Website">
</a>
<a href="https://androidweekly.net/issues/issue-739" target="_blank">
  <img src="https://androidweekly.net/issues/issue-739/badge"
       alt="Featured in androidweekly.net Issue #739">
</a>

<p>
  <img src="docs/images/hero_left.jpg" width="216" alt="PC HW Monitor RAM and Disk cards">
  <img src="docs/images/main_ember.jpg" width="264" alt="PC HW Monitor CPU and GPU dashboard">
  <img src="docs/images/hero_right.jpg" width="216" alt="PC HW Monitor Settings">
</p>

<p>
  <a href="https://github.com/Xeakaes/PcHWmonitor/releases"><strong>Download</strong></a> ·
  <a href="#english">English</a> ·
  <a href="#turkce">Türkçe</a>
</p>

</div>

---

<sup><strong>License:</strong> [AGPL-3.0](LICENSE) — the Windows EXE bundles
[LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor)
(`LibreHardwareMonitorLib.dll`), also AGPL-3.0.
<strong>Support:</strong> [Patreon](https://www.patreon.com/cw/Obscrum)</sup>
```

Then insert immediately before the existing `## English` heading (was line 27, now shifted): `<a id="english"></a>`. Insert immediately before the existing `## Türkçe` heading: `<a id="turkce"></a>`. Do not modify the `## English` / `## Türkçe` lines themselves yet.

- [ ] **Step 2: Verify hero renders**

Run:
```bash
git diff README.md
```
Expected: only the header block (old lines 1-25) replaced, plus the two anchor lines added; `## English` and `## Türkçe` headings still present and unchanged. Count check:
```bash
grep -c "docs/images/" README.md
```
Expected: `3` (hero_left, main_ember, hero_right). Verify image files exist:
```bash
ls -la docs/images/hero_left.jpg docs/images/main_ember.jpg docs/images/hero_right.jpg
```
Expected: all three listed.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add hero section with 3-layer showcase to README"
```

---

### Task 2: English section restructure

**Files:**
- Modify: `README.md` — the whole `## English` region (currently lines 27-159: Features, Architecture, Windows EXE, Server Setup, Android Setup, WebSocket Protocol, Troubleshooting, Notes)

**Interfaces:**
- Consumes: `<a id="english"></a>` anchor from Task 1.
- Produces: Nine numbered English sections in the exact order listed in Global Constraints. The later Task 3 mirrors the same nine-section structure.

- [ ] **Step 1: Insert the new section headers and new content**

Inside the `## English` region, restructure to exactly this skeleton (the `###` headers replace the current ones — **move** existing content rather than deleting it):

```markdown
## English

### 1. Features

(move existing "Features" bullet list from current lines 29-39 verbatim;
 the final line of that list is "Packaged FPS support uses an embedded `PresentMon64.exe` (see Building the EXE).")

### 2. Screenshots

| | |
|---|---|
| ![Dashboard (dark)](docs/images/main_ember.jpg) | ![Dashboard (light)](docs/images/main_light.jpg) |
| ![Dashboard (landscape)](docs/images/main_landscape.jpg) | ![Settings](docs/images/settings_light.jpg) |

### 3. Download

- **Android:** [GitHub Releases](https://github.com/Xeakaes/PcHWmonitor/releases) — latest APK. Also submitted to [F-Droid](https://f-droid.org/) (awaiting review).
- **Windows:** `PcHwMonitor.exe` — single-file server, built with `build_exe.bat` (see Getting Started).

### 4. Architecture

(move existing "Architecture" code block + explanation from current lines 41-53 verbatim)

### 5. Getting Started

#### Windows EXE (recommended)

(move current "Windows EXE (recommended path)" numbered steps 1-3 and the rebuild note + psutil/PresentMon paragraphs from lines 55-63 verbatim)

#### Server Setup (development)

(move the Windows real-data setup block + options list + simulate mode + verify block from lines 65-98 verbatim)

#### Android Setup

(move the gradle build/install/test block from lines 100-107 verbatim)

### 6. WebSocket Protocol

(move the JSON example blocks + compatibility paragraph from lines 109-143 verbatim)

### 7. Troubleshooting

(move the existing table from lines 145-153 verbatim)

### 8. FAQ

**Does it work over the internet?**
No. PC HW Monitor runs on your local Wi-Fi network only — both devices must be on the same network. No accounts, no cloud.

**Why does the Windows EXE need administrator rights?**
Reading CPU temperature requires hardware access that Windows only grants to elevated processes (UAC). Accept the prompt once when starting.

**Can I try it without a PC?**
Yes. The server has a `--simulate` mode that generates realistic fake data — works on any OS, ideal for testing.

**Which languages does the app support?**
14 languages, selectable in Settings.

**Is it free?**
Yes, AGPL-3.0. Development is supported by Patreon patrons.

### 9. License

[GNU AGPL v3](LICENSE) (GNU Affero General Public License, version 3).

- The Windows EXE bundles [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) (`LibreHardwareMonitorLib.dll`), also AGPL-3.0. When distributing the EXE, the corresponding source and license must be made available (see section 13 of the AGPL).
- The Android app and server source code are licensed under AGPL-3.0.
```

Remove the old `### Notes` section content only after confirming its three bullets are already covered: FPS card note (now part of Features/Getting Started content moved verbatim), Disk/Net/Fan null-cards note (covered by the "old server payloads" paragraph moved in section 6), history/Room DB note (covered by the Features list). If any bullet is NOT covered, keep that bullet under section 6 instead of deleting it.

- [ ] **Step 2: Verify the English half**

Run:
```bash
grep -n "^### " README.md
```
Expected: `### 1. Features` through `### 9. License` in order, and no stray `### Notes` or `### Windows EXE (recommended path)` headers. Also:
```bash
grep -c "^### 5. Getting Started" README.md && grep -c "## English" README.md
```
Expected: `1` and `1`.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: restructure English sections with gallery, download and FAQ"
```

---

### Task 3: Turkish section restructure (mirror)

**Files:**
- Modify: `README.md` — the whole `## Türkçe` region (currently lines 164-296: Özellikler, Mimari, Windows EXE, Sunucu Kurulumu, Android Kurulumu, WebSocket Protokolü, Sorun Giderme, Notlar)

**Interfaces:**
- Consumes: `<a id="turkce"></a>` anchor from Task 1; section order established in Task 2.
- Produces: Mirrored Turkish sections; the final file has all nine EN and all nine TR sections.

- [ ] **Step 1: Restructure the Türkçe region**

Inside the `## Türkçe` region, use this skeleton — every `###` heading translated exactly as shown, existing Turkish content moved verbatim from the line refs, and new content written below verbatim:

```markdown
## Türkçe

### 1. Özellikler

(move the existing Turkish Features bullet list from current lines 168-176 verbatim)

### 2. Ekran Görüntüleri

| | |
|---|---|
| ![Dashboard (koyu)](docs/images/main_ember.jpg) | ![Dashboard (açık)](docs/images/main_light.jpg) |
| ![Dashboard (yatay)](docs/images/main_landscape.jpg) | ![Ayarlar](docs/images/settings_light.jpg) |

### 3. İndirme

- **Android:** [GitHub Releases](https://github.com/Xeakaes/PcHWmonitor/releases) — en güncel APK. Ayrıca [F-Droid](https://f-droid.org/) listesine gönderildi (inceleme bekleniyor).
- **Windows:** `PcHwMonitor.exe` — tek dosyalık sunucu; `build_exe.bat` ile derlenir (bkz. Başlarken).

### 4. Mimari

(move the existing Turkish Architecture block + explanation from lines 178-190 verbatim)

### 5. Başlarken

#### Windows EXE (önerilen yol)

(move the current Turkish EXE steps 1-3 + rebuild + psutil/PresentMon paragraphs from lines 192-200 verbatim)

#### Sunucu Kurulumu (geliştirme)

(move the Turkish server setup blocks from lines 202-235 verbatim)

#### Android Kurulumu

(move the Turkish gradle block from lines 237-244 verbatim)

### 6. WebSocket Protokolü

(move the Turkish JSON example blocks + compatibility paragraph from lines 246-280 verbatim)

### 7. Sorun Giderme

(move the existing Turkish table from lines 282-290 verbatim)

### 8. SSS

**İnternet üzerinden çalışır mı?**
Hayır. PC HW Monitor yalnızca yerel Wi-Fi ağında çalışır — her iki cihaz da aynı ağda olmalıdır. Hesap ve bulut yok.

**Windows EXE neden yönetici hakları istiyor?**
CPU sıcaklığını okumak, Windows'un yalnızca yükseltilmiş süreçlere (UAC) verdiği donanım erişimini gerektirir. Başlatırken istemi bir kez onaylayın.

**PC'siz deneyebilir miyim?**
Evet. Sunucunun `--simulate` modu gerçekçi sahte veri üretir — her işletim sisteminde çalışır, test için idealdir.

**Uygulama kaç dil destekliyor?**
Ayarlardan seçilebilen 14 dil.

**Ücretsiz mi?**
Evet, AGPL-3.0 lisanslı. Geliştirme Patreon destekçileri sayesinde sürüyor.

### 9. Lisans

[GNU AGPL v3](LICENSE) (GNU Affero General Public License, sürüm 3).

- Windows EXE, yine AGPL-3.0 olan [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) (`LibreHardwareMonitorLib.dll`) dosyasını paketler. EXE dağıtımında karşılık gelen kaynak kod ve lisansın sunulması gerekir (AGPL madde 13).
- Android uygulaması ve sunucu kaynak kodu AGPL-3.0 ile lisanslıdır.
```

Remove the old `## Lisans / License` merged section at the bottom of the file (current lines 298-306) after confirming its content is fully covered by the new `### 9. Lisans` block above.

- [ ] **Step 2: Verify the Turkish half and final structure**

Run:
```bash
grep -n "^## \|^### [0-9]" README.md
```
Expected, in order: `## English`, then `### 1.` through `### 9.`, blank, `## Türkçe`, then `### 1.` through `### 9.` — no `## Lisans / License` anywhere. Also:
```bash
grep -c 'docs/images/' README.md
```
Expected: `11` (3 hero + 4 EN gallery + 4 TR gallery).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: mirror restructured sections in Turkish"
```

---

### Task 4: Final verification

**Files:**
- Modify: `README.md` (only if bugs are found)

**Interfaces:**
- Consumes: the full rewritten README from Tasks 1-3.

- [ ] **Step 1: Verify all local image references resolve**

Run:
```bash
grep -o 'docs/images/[a-z_]*\.jpg' README.md | sort -u | while read img; do test -f "$img" || echo "MISSING: $img"; done
```
Expected: no `MISSING:` output. Six unique files used, all within `docs/images/`.

- [ ] **Step 2: Verify external links and anchors**

Run:
```bash
grep -o 'href="[^"]*"\|](https\?://[^)]*)' README.md | sort -u
```
Expected: the four badge URLs (codeql.yml badge, alternativeto.net, xeakaes.github.io, androidweekly.net) appear in `href=` form; Patreon, LICENSE, LibreHardwareMonitor, GitHub Releases and F-Droid links appear in markdown form; every URL matches one already present in the previous README except the three new ones allowed by the plan (GitHub Releases, F-Droid, and the `#english`/`#turkce` anchors which appear in `href="#english"` and `href="#turkce"` form). If any `](url)` target is malformed (e.g. missing `)`), fix it.

- [ ] **Step 3: Verify no content was lost**

Compare section content: for each of Features/Architecture/Protocol JSON/Troubleshooting table, confirm the exact code blocks and tables from the old README (per `git show HEAD:README.md`) appear verbatim in the new file. Verify the protocol example still contains both `welcome`+`status` blocks and the compatibility paragraph; the Features list still mentions all 5 palettes, edit mode, landscape mode, and 14 languages; the EXE section still names `dist\PcHwMonitor.exe`, port `8765` and the tray behavior. Check with `git diff HEAD README.md --stat` that removed lines consist only of section headers/reorganizations, not content.

- [ ] **Step 4: Final read-through and commit**

Read `README.md` top to bottom (Read tool, 2-3 chunks). Check: no emojis, no "TBD", no duplicate sections, EN/TR headings match the skeleton, hero images not cropped by missing width attributes, Turkish diacritics intact (e.g. `Bağlantı yok`, `Türkçe`). If any fix was needed in steps 1-3, commit it:

```bash
git add README.md
git commit -m "docs: fix README verification issues"
```

If nothing needed fixing, no commit is required in this task — state that the README is complete.