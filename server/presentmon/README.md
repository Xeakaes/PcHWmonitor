# PresentMon binary for FPS monitoring

The server pipes PresentMon's stdout to compute FPS for a target process. To
enable FPS, place the official PresentMon binary in this folder as
`PresentMon64.exe`.

## Download

Get the official release from the repository:

https://github.com/GameTechDev/PresentMon/releases/latest

Release assets are named like `PresentMon-<version>-x64.exe`. Download the
64-bit console application, rename it to exactly `PresentMon64.exe`, and save
it in this folder:

    server/presentmon/PresentMon64.exe

If the binary is missing, the server logs `PresentMon unavailable; FPS
disabled` and continues running; only the FPS metric is left unset.

## Git

The binary is intentionally NOT committed to git.
`server/presentmon/PresentMon64.exe` is in the project `.gitignore`.
`build_exe.bat` bundles it into the PyInstaller one-file build via
`--add-data "%PRESENTMON%;."`.

On systems without the binary (e.g. Linux, or a frozen build without it) the
server still starts and reports every other metric normally.

## License verification

PresentMon is distributed under the MIT license. Verify that the release you
use carries the license you agree with before bundling it:

https://github.com/GameTechDev/PresentMon/blob/main/LICENSE