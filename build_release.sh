#!/bin/bash
set -e

REPO="/home/xeakaes/PcHWmonitor"
WIN="/mnt/c/Users/msi/PcHWmonitor"
CLOUDFLARED_SRC="/mnt/c/Users/Public/PcHWbuild/server/vendor/cloudflared.exe"
RELEASE_ID=389185004
GH_TOKEN="${GH_TOKEN:?Set GH_TOKEN environment variable}"
REPO_SLUG="Xeakaes/PcHWmonitor"

echo "=== 1. Rsync code to Windows ==="
rsync -av --delete \
  --exclude='.git' \
  --exclude='build' \
  --exclude='.gradle' \
  --exclude='local.properties' \
  --exclude='server/.venv_win' \
  --exclude='server/build' \
  --exclude='server/dist' \
  --exclude='server/vendor' \
  "$REPO/" "$WIN/"

echo "=== 2. Ensure vendor dir + cloudflared.exe ==="
mkdir -p "$WIN/server/vendor"
if [ -f "$CLOUDFLARED_SRC" ]; then
    cp -f "$CLOUDFLARED_SRC" "$WIN/server/vendor/cloudflared.exe"
    echo "cloudflared.exe copied ($(du -h "$WIN/server/vendor/cloudflared.exe" | cut -f1))"
else
    echo "WARNING: cloudflared.exe not found at $CLOUDFLARED_SRC"
fi

echo "=== 3. Create build script ==="
cat > "$WIN/build_exe_win.bat" << 'BATEOF'
@echo off
setlocal
cd /d C:\Users\msi\PcHWmonitor\server
if not exist .venv_win\Scripts\python.exe (
    echo Creating Windows venv...
    "C:\Users\msi\AppData\Local\Python\bin\python.exe" -m venv .venv_win
)
set PY=.venv_win\Scripts\python.exe
%PY% -m pip install --quiet pyinstaller pystray Pillow psutil pythonnet fastapi uvicorn websockets httpx qrcode
set VENDOR=vendor
set LHMDIR=C:\Users\msi\LibreHardwareMonitor
if not exist "%VENDOR%" mkdir "%VENDOR%"
if exist "%LHMDIR%\*.dll" copy /y "%LHMDIR%\*.dll" "%VENDOR%" >nul
%PY% -m PyInstaller --onefile --noconsole --uac-admin --name PcHwMonitor --hidden-import psutil --hidden-import pystray --hidden-import PIL --hidden-import pythonnet --hidden-import httpx --hidden-import qrcode --hidden-import uvicorn --hidden-import uvicorn.logging --hidden-import uvicorn.loops --hidden-import uvicorn.loops.auto --hidden-import uvicorn.protocols --hidden-import uvicorn.protocols.http --hidden-import uvicorn.protocols.http.auto --hidden-import uvicorn.protocols.websockets --hidden-import uvicorn.protocols.websockets.auto --hidden-import uvicorn.lifespan --hidden-import uvicorn.lifespan.on --hidden-import fastapi --hidden-import starlette --add-data "%VENDOR%;." main.py
endlocal
BATEOF

echo "=== 4. Build EXE ==="
cmd.exe /c "C:\Users\msi\PcHWmonitor\build_exe_win.bat"

EXE_SIZE=$(du -h "$WIN/server/dist/PcHwMonitor.exe" | cut -f1)
echo "EXE built: $EXE_SIZE"

echo "=== 5. Build APK ==="
cat > "$WIN/build_apk.bat" << 'APKBAT'
@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d C:\Users\msi\PcHWmonitor
call gradlew.bat assembleRelease
APKBAT
cmd.exe /c "C:\Users\msi\PcHWmonitor\build_apk.bat"
APK_SIZE=$(du -h "$WIN/app/build/outputs/apk/release/app-release.apk" | cut -f1)
echo "APK built: $APK_SIZE"

echo "=== 6. Delete old release assets ==="
curl -s -H "Authorization: token $GH_TOKEN" \
  "https://api.github.com/repos/$REPO_SLUG/releases/$RELEASE_ID/assets" | python3 -c "
import json,sys,urllib.request
for a in json.load(sys.stdin):
    req = urllib.request.Request(f'https://api.github.com/repos/$REPO_SLUG/releases/assets/{a[\"id\"]}', method='DELETE')
    req.add_header('Authorization', 'token $GH_TOKEN')
    urllib.request.urlopen(req)
    print(f'  deleted {a[\"name\"]}')
"

echo "=== 7. Upload new APK ==="
curl -s -H "Authorization: token $GH_TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$WIN/app/build/outputs/apk/release/app-release.apk" \
  "https://uploads.github.com/repos/$REPO_SLUG/releases/$RELEASE_ID/assets?name=PcHwMonitor-v1.6.apk" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f'  uploaded {d[\"name\"]} ({d[\"size\"]} bytes)')"

echo "=== 8. Upload new EXE ==="
curl -s -H "Authorization: token $GH_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @"$WIN/server/dist/PcHwMonitor.exe" \
  "https://uploads.github.com/repos/$REPO_SLUG/releases/$RELEASE_ID/assets?name=PcHwMonitor.exe" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f'  uploaded {d[\"name\"]} ({d[\"size\"]} bytes)')"

echo "=== DONE ==="
