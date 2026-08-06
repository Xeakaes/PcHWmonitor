@echo off
REM PC HW Monitor - single-file Windows EXE build (run from project root)
setlocal
set PY=server\.venv\Scripts\python.exe
set LHMDIR=C:\Users\msi\LibreHardwareMonitor
set VENDOR=server\vendor
set PRESENTMON=server\presentmon\PresentMon64.exe
if not exist "%VENDOR%" mkdir "%VENDOR%"
copy /y "%LHMDIR%\*.dll" "%VENDOR%" >nul
set PRESENTMON_ARGS=
if not exist "%PRESENTMON%" goto :no_presentmon
REM stage the binary so the onefile build can reference it
copy /y "%PRESENTMON%" "%TMP%\PresentMon64.exe" >nul
set PRESENTMON_ARGS=--add-data "%PRESENTMON%;."
goto :build
:no_presentmon
echo WARNING: %PRESENTMON% not found - FPS will be disabled in this build
:build
%PY% -m PyInstaller --onefile --noconsole --uac-admin --name PcHwMonitor --add-data "%VENDOR%;." %PRESENTMON_ARGS% server\main.py
endlocal