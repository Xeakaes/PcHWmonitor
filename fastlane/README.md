# Fastlane configuration for PC HW Monitor

## Setup

Install fastlane:

```sh
gem install fastlane
```

Or use bundler (recommended):

```sh
bundle install
```

## Available lanes

- `build` — assemble the debug APK

## Metadata

- `fastlane/metadata/android/en-US/` — English store listing
- `fastlane/metadata/android/tr/` — Turkish store listing
- `fastlane/metadata/android/<locale>/screenshots/` — screenshots per locale

## Appfile

- `package_id` — `com.Obscrum.pchwmonitor`
