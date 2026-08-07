@echo off
REM PC HW Monitor - single-file Windows EXE build (run from project root)
setlocal
set PY=server\.venv\Scripts\python.exe
if not defined LHMDIR set LHMDIR=C:\Users\msi\LibreHardwareMonitor
set VENDOR=server\vendor
set PRESENTMON=server\presentmon\PresentMon64.exe
if not exist "%VENDOR%" mkdir "%VENDOR%"
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