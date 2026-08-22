# Releasing (manual signing policy)

The signing keystore never leaves the maintainer's machine. CI does not sign
and does not attach assets — pushing a `v*` tag only opens a release shell
with generated notes. The signed APK and the Windows EXE are attached by hand.

## 1. Bump the version

`app/build.gradle.kts`:

```kotlin
versionCode = <previous + 1>
versionName = "<x.y>"
```

Commit: `release: bump versionCode N, versionName x.y`

## 2. Build and sign the APK

Requires a local `key.properties` at the repo root (git-ignored):

```properties
storeFile=<absolute path to keystore>
storePassword=<...>
keyAlias=<...>
keyPassword=<...>
```

Build:

```bash
./gradlew --no-daemon :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

Verify the signature matches the release certificate before uploading —
a mismatched key makes the update uninstallable for existing users:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

## 3. Build the Windows EXE

On Windows, from the project root (needs Python + the LHM DLLs, see
`build_exe.bat`; FPS needs `server/presentmon/PresentMon64.exe`):

```bat
build_exe.bat
:: output: dist\PcHwMonitor.exe
```

Smoke-test it before publishing:

```bat
dist\PcHwMonitor.exe --simulate --port 8765 --token test123
```

Then check `http://<pc-ip>:8765/health` returns `"ok": true`, connect with
the right token, and close the tray app.

## 4. Tag and publish

```bash
git tag vX.Y && git push origin vX.Y
```

The tag push opens the GitHub release with generated notes. Attach manually:

- `PcHwMonitor-vX.Y.apk` (signed APK from step 2)
- `PcHwMonitor.exe` (from step 3)

Finally, download both assets back from the release page and re-run the
signature/health checks on them — what is published must be verified as
published.
