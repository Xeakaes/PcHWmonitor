@echo off
REM PC HW Monitor - single-file Windows EXE build (run from project root)
setlocal
set PY=server\.venv\Scripts\python.exe
set LHMDIR=C:\Users\msi\LibreHardwareMonitor
set VENDOR=server\vendor
if not exist "%VENDOR%" mkdir "%VENDOR%"
copy /y "%LHMDIR%\*.dll" "%VENDOR%" >nul
%PY% -m PyInstaller --onefile --noconsole --uac-admin --name PcHwMonitor --add-data "%VENDOR%;." server\main.py
endlocal
