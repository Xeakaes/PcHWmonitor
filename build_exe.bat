@echo off
REM PC HW Monitor - single-file Windows EXE build (run from project root)
setlocal
set PY=server\.venv\Scripts\python.exe
if not defined LHMDIR set LHMDIR=C:\Users\msi\LibreHardwareMonitor
set VENDOR=server\vendor
set PRESENTMON=server\presentmon\PresentMon64.exe
if not defined CLOUDFLARED_VER set CLOUDFLARED_VER=2026.9.3
if not exist "%VENDOR%" mkdir "%VENDOR%"
REM embed cloudflared.exe so the tunnel works without a side-by-side binary
if not exist "%VENDOR%\cloudflared.exe" (
    echo Downloading cloudflared %CLOUDFLARED_VER% into %VENDOR%...
    curl -fsSL -o "%VENDOR%\cloudflared.exe" "https://github.com/cloudflare/cloudflared/releases/download/%CLOUDFLARED_VER%/cloudflared-windows-amd64.exe"
    if errorlevel 1 (
        echo WARNING: cloudflared download failed - tunnel disabled in this build
        if exist "%VENDOR%\cloudflared.exe" del /q "%VENDOR%\cloudflared.exe"
    )
)
if exist "%LHMDIR%\*.dll" (
    copy /y "%LHMDIR%\*.dll" "%VENDOR%" >nul
) else (
    echo WARNING: No DLLs found in %LHMDIR% - hardware sensors disabled in this build
)
set PRESENTMON_ARGS=
if not exist "%PRESENTMON%" goto :no_presentmon
REM stage the binary so the onefile build can reference it
copy /y "%PRESENTMON%" "%TMP%\PresentMon64.exe" >nul
set PRESENTMON_ARGS=--add-data "%PRESENTMON%;."
goto :build
:no_presentmon
echo WARNING: %PRESENTMON% not found - FPS will be disabled in this build
:build
%PY% -m PyInstaller --onefile --noconsole --uac-admin --name PcHwMonitor --hidden-import psutil --hidden-import pystray --hidden-import PIL --hidden-import pythonnet --add-data "%VENDOR%;." %PRESENTMON_ARGS% server\main.py
endlocal