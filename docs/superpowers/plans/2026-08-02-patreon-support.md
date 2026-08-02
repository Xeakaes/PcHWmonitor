# Patreon Support Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Patreon support entry point on GitHub (sidebar Sponsor button) and at the bottom of the Android app's Settings tab.

**Architecture:** A `FUNDING.yml` with a custom URL renders the GitHub Sponsor button; on Android, a URL constant in `util/DonateLinks.kt` is opened by a new "Support" section appended to the bottom of `SettingsScreen`, with 3 new localized strings in all 14 language files. Dashboard and all other screens are untouched.

**Tech Stack:** Kotlin, Jetpack Compose, Android Intents, YAML.

## Global Constraints

- Patreon URL (exact): `https://www.patreon.com/cw/Obscrum` — note the `/cw/` prefix; do NOT use `patreon.com/Obscrum`.
- Main dashboard screen (`DashboardScreen.kt`) must NOT be modified.
- 14 strings files must stay in key parity: every key present in all 14 files or build fails (Resources$NotFoundException at runtime).
- No new dependencies, no new icons/assets.
- Comment rule: section-header comments only, in English; no line-by-line comments.
- This repo IS a git repo (origin: https://github.com/Xeakaes/PcHWmonitor.git). Commit after every step group. Do NOT push unless the task says so.

---

### Task 1: URL constant + unit test (TDD)

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/util/DonateLinks.kt`
- Test: `app/src/test/java/com/example/pchwmonitor/DonateLinksTest.kt`

**Interfaces:**
- Produces: `package com.example.pchwmonitor.util; const val PATREON_URL: String = "https://www.patreon.com/cw/Obscrum"` — consumed by Task 3 (Settings button) and Task 5 (release).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/pchwmonitor/DonateLinksTest.kt`:

```kotlin
package com.example.pchwmonitor

import com.example.pchwmonitor.util.PATREON_URL
import org.junit.Assert.assertEquals
import org.junit.Test

class DonateLinksTest {
    @Test
    fun patreonUrlIsCorrect() {
        assertEquals("https://www.patreon.com/cw/Obscrum", PATREON_URL)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:testDebugUnitTest --tests "com.example.pchwmonitor.DonateLinksTest" --console=plain`
Expected: compile error — unresolved reference `PATREON_URL`.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/example/pchwmonitor/util/DonateLinks.kt`:

```kotlin
package com.example.pchwmonitor.util

const val PATREON_URL: String = "https://www.patreon.com/cw/Obscrum"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:testDebugUnitTest --tests "com.example.pchwmonitor.DonateLinksTest" --console=plain`
Expected: 1 test, PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/pchwmonitor/util/DonateLinks.kt app/src/test/java/com/example/pchwmonitor/DonateLinksTest.kt
git commit -m "feat: add Patreon URL constant"
```

---

### Task 2: Localized strings (14 files)

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (and every `values-*/strings.xml`, 14 files total)

**Interfaces:**
- Produces: string resources `support`, `support_description`, `support_patreon` in all 14 files — consumed by Task 3 via `R.string.*`.

- [ ] **Step 1: Add the 3 keys to `values/strings.xml` (English)**

Insert after the `<string name="saved">` line:

```xml
    <string name="support">Support</string>
    <string name="support_description">This app is free and open source. If it is useful to you, consider supporting the development on Patreon.</string>
    <string name="support_patreon">Support on Patreon</string>
```

- [ ] **Step 2: Add the 3 keys to the other 13 files with these exact translations**

`values-tr/strings.xml`:

```xml
    <string name="support">Destek</string>
    <string name="support_description">Bu uygulama ücretsiz ve açık kaynak. İşine yaradıysa geliştirmeyi Patreon üzerinden destekleyebilirsin.</string>
    <string name="support_patreon">Patreon\'da destekle</string>
```

`values-de/strings.xml`:

```xml
    <string name="support">Unterstützung</string>
    <string name="support_description">Diese App ist kostenlos und Open Source. Wenn sie dir nützt, kannst du die Entwicklung auf Patreon unterstützen.</string>
    <string name="support_patreon">Auf Patreon unterstützen</string>
```

`values-es/strings.xml`:

```xml
    <string name="support">Apoyar</string>
    <string name="support_description">Esta aplicación es gratuita y de código abierto. Si te resulta útil, puedes apoyar el desarrollo en Patreon.</string>
    <string name="support_patreon">Apoyar en Patreon</string>
```

`values-fr/strings.xml`:

```xml
    <string name="support">Soutien</string>
    <string name="support_description">Cette application est gratuite et open source. Si elle vous est utile, vous pouvez soutenir le développement sur Patreon.</string>
    <string name="support_patreon">Soutenir sur Patreon</string>
```

`values-it/strings.xml`:

```xml
    <string name="support">Supporto</string>
    <string name="support_description">Questa app è gratuita e open source. Se ti è utile, puoi sostenere lo sviluppo su Patreon.</string>
    <string name="support_patreon">Sostieni su Patreon</string>
```

`values-ja/strings.xml`:

```xml
    <string name="support">支援</string>
    <string name="support_description">このアプリは無料のオープンソースです。役に立つと思ったら、Patreonで開発を支援できます。</string>
    <string name="support_patreon">Patreonで支援する</string>
```

`values-nl/strings.xml`:

```xml
    <string name="support">Steun</string>
    <string name="support_description">Deze app is gratis en open source. Als hij nuttig is, kun je de ontwikkeling steunen op Patreon.</string>
    <string name="support_patreon">Steunen op Patreon</string>
```

`values-pl/strings.xml`:

```xml
    <string name="support">Wsparcie</string>
    <string name="support_description">Ta aplikacja jest darmowa i open source. Jeśli jest dla Ciebie przydatna, możesz wesprzeć rozwój na Patreon.</string>
    <string name="support_patreon">Wesprzyj na Patreon</string>
```

`values-pt/strings.xml` and `values-pt-rBR/strings.xml` (same text in both):

```xml
    <string name="support">Apoiar</string>
    <string name="support_description">Esta aplicação é gratuita e de código aberto. Se for útil para você, pode apoiar o desenvolvimento no Patreon.</string>
    <string name="support_patreon">Apoiar no Patreon</string>
```

`values-ru/strings.xml`:

```xml
    <string name="support">Поддержка</string>
    <string name="support_description">Это приложение бесплатное и с открытым исходным кодом. Если оно полезно для вас, вы можете поддержать разработку на Patreon.</string>
    <string name="support_patreon">Поддержать на Patreon</string>
```

`values-zh/strings.xml`:

```xml
    <string name="support">支持</string>
    <string name="support_description">这个应用是免费开源的。如果它对你有用，可以在 Patreon 上支持开发。</string>
    <string name="support_patreon">在 Patreon 上支持</string>
```

`values-zh-rTW/strings.xml`:

```xml
    <string name="support">支持</string>
    <string name="support_description">這個應用程式是免費開源的。如果對你有用，可以在 Patreon 上支持開發。</string>
    <string name="support_patreon">在 Patreon 上支持</string>
```

- [ ] **Step 3: Verify key parity across all 14 files**

Run:

```bash
python3 - <<'EOF'
import glob, re
keys = {}
for f in sorted(glob.glob('/home/xeakaes/PcHWmonitor/app/src/main/res/values*/strings.xml')):
    k = set(re.findall(r'name="([^"]+)"', open(f).read()))
    keys[f] = k
base = set(next(iter(keys.values())))
for f, k in keys.items():
    assert k == base, f"MISMATCH in {f}: missing={base-k} extra={k-base}"
print("OK — all files have", len(base), "keys")
EOF
```

Expected: `OK — all files have 59 keys`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/
git commit -m "feat: add support strings in 14 languages"
```

---

### Task 3: Settings screen Support section + version bump

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/navigation/AppNavHost.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `PATREON_URL` from Task 1; `R.string.support`, `R.string.support_description`, `R.string.support_patreon` from Task 2.
- Produces: 3 new parameters on `SettingsScreen` (`labelSupport: String`, `labelSupportDescription: String`, `labelSupportPatreon: String`) — the AppNavHost call site supplies them.

- [ ] **Step 1: Add the 3 label parameters to `SettingsScreen`**

In `SettingsScreen.kt`, after the `labelSaved: String,` line (line ~56), add:

```kotlin
    labelSupport: String,
    labelSupportDescription: String,
    labelSupportPatreon: String,
```

- [ ] **Step 2: Add the Support section at the bottom of the Column**

In `SettingsScreen.kt`, replace the final block (lines ~174-182):

```kotlin
        if (saved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = labelSaved,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
```

with:

```kotlin
        if (saved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = labelSaved,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = labelSupport,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = labelSupportDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PATREON_URL)))
            },
        ) {
            Text(labelSupportPatreon)
        }
        Spacer(modifier = Modifier.height(32.dp))
```

- [ ] **Step 3: Add the `context` val and imports to `SettingsScreen.kt`**

At the top of the `SettingsScreen` composable body (before `var ip by ...`), add:

```kotlin
    val context = LocalContext.current
```

Add these imports (alphabetical order, after the existing `androidx.compose.material3.*` imports):

```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.example.pchwmonitor.util.PATREON_URL
```

- [ ] **Step 4: Wire the labels at the AppNavHost call site**

In `AppNavHost.kt`, after the `labelSaved = stringResource(R.string.saved),` line (~155), add:

```kotlin
                    labelSupport = stringResource(R.string.support),
                    labelSupportDescription = stringResource(R.string.support_description),
                    labelSupportPatreon = stringResource(R.string.support_patreon),
```

- [ ] **Step 5: Version bump in `app/build.gradle.kts`**

Replace lines 18-19:

```kotlin
        versionCode = 1
        versionName = "1.0"
```

with:

```kotlin
        versionCode = 2
        versionName = "1.1"
```

- [ ] **Step 6: Build + run all tests**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL; 25 unit tests pass (24 existing + DonateLinksTest).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java app/build.gradle.kts
git commit -m "feat: add support section to Settings, bump to 1.1"
```

---

### Task 4: GitHub sidebar Sponsor button

**Files:**
- Create: `.github/FUNDING.yml`
- Modify: `README.md`

- [ ] **Step 1: Create `.github/FUNDING.yml`**

```yaml
custom: ["https://www.patreon.com/cw/Obscrum"]
```

- [ ] **Step 2: Add a Support section to `README.md`**

Insert right after the header block (after the license line, before the `---`):

```markdown
**Support:** [Patreon](https://www.patreon.com/cw/Obscrum) — free software stays free thanks to supporters.
**Destek:** [Patreon](https://www.patreon.com/cw/Obscrum) — ücretsiz yazılım, destekçiler sayesinde ücretsiz kalır.
```

- [ ] **Step 3: Commit**

```bash
git add .github/FUNDING.yml README.md
git commit -m "feat: add Patreon sponsor button and README support section"
```

- [ ] **Step 4: Push all commits**

Run: `git push`
Expected: all 4 feature commits pushed (can be done from Windows side if credentials; on this machine push with the token URL).

- [ ] **Step 5: Verify the Sponsor button**

Fetch the repo page HTML and confirm the button renders:

Run: `curl -s https://github.com/Xeakaes/PcHWmonitor | grep -o 'Sponsor[^<]*' | head -3`
Expected: output containing "Sponsor".

---

### Task 5: Release v1.1.0

**Files:**
- None (uses the APK from Task 3, step 6: `app/build/outputs/apk/debug/app-debug.apk`)

- [ ] **Step 1: Verify the APK contains version 1.1**

Run: `unzip -p app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml | strings | grep -o '1\.1' | head -1`
Expected: `1.1`.

- [ ] **Step 2: Sync the repo to the Windows copy (for future builds)**

Run: `rsync -a --exclude .venv --exclude __pycache__ --exclude app/build --exclude build --exclude .gradle --exclude .kotlin --exclude local.properties --exclude .git /home/xeakaes/PcHWmonitor/ /mnt/c/Users/msi/PcHWmonitor/`

- [ ] **Step 3: Create the release via API (use the user's GitHub token)**

Run:

```bash
TOKEN="<user's fine-grained PAT with Contents read+write>"
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/Xeakaes/PcHWmonitor/releases \
  -d '{"tag_name":"v1.1.0","name":"PC HW Monitor v1.1.0","body":"### Added\n- Patreon support section in the Settings tab (14 languages)\n- GitHub Sponsor button (FUNDING.yml)\n\n### Download\n- APK only: server EXE unchanged since v1.0.0 (https://github.com/Xeakaes/PcHWmonitor/releases/tag/v1.0.0)\n\nLicense: AGPL-3.0. The EXE bundles LibreHardwareMonitor (AGPL-3.0).","draft":false,"prerelease":false}'
```

Capture the `id` from the response.

- [ ] **Step 4: Upload the APK asset**

Run (replace `<RELEASE_ID>` with the id from step 3):

```bash
TOKEN="<user's fine-grained PAT with Contents read+write>"
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/octet-stream" \
  --data-binary @app/build/outputs/apk/debug/app-debug.apk \
  "https://uploads.github.com/repos/Xeakaes/PcHWmonitor/releases/<RELEASE_ID>/assets?name=PcHwMonitor-v1.1.0.apk"
```

- [ ] **Step 5: Verify the release**

Run: `curl -s https://api.github.com/repos/Xeakaes/PcHWmonitor/releases/tags/v1.1.0 | python3 -c "import json,sys; r=json.load(sys.stdin); print(r['tag_name'], [a['name'] for a in r['assets']])"`
Expected: `v1.1.0 ['PcHwMonitor-v1.1.0.apk']`.

---

## Self-review notes

- Spec coverage: GitHub FUNDING.yml → Task 4; README support lines → Task 4; Settings bottom section → Task 3; 14-file strings → Task 2; DonateLinks constant + test → Task 1; version bump 1.1 → Task 3; release v1.1.0 → Task 5; dashboard untouched → no task touches DashboardScreen.
- No placeholders; every step has concrete content.
- Type/signature consistency: `PATREON_URL` const defined Task 1, used Task 3/5; string keys `support`/`support_description`/`support_patreon` defined Task 2, used Task 3; `SettingsScreen` params named `labelSupport*`, matched in AppNavHost call site.
