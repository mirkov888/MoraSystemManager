# Mora System Manager

Android root utility app — Kotlin, Jetpack Compose, Material You dynamic colors.

## Alpha 03 — Launcher Icon Settings
- Four switchable launcher icon variants in Settings
- Default icon is variant 2 (purple Mora shield)
- Uses Android activity-alias components; no root permission is needed to change icon
- Icon selection persists through PackageManager component state
- Future ideas: Privacy cat mascot icon, themed monochrome/adaptive icons, appearance presets, system health center, safe debloat profiles

## Alpha 02 — Package Manager V2
- Installed app inventory, search and filters
- App details: package ID, version, UID, APK path, enabled state
- Enable/disable and uninstall user apps for Android user 0
- System app uninstall guard
- Export package inventory
- Root preferred; Shizuku fallback

## Requirements
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.7.3

## Build
Open in Android Studio, sync Gradle, then run `assembleDebug`.
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Important
Changing the launcher icon does not require root. Package operations do. Disable operations can break device features; keep core Android packages enabled.
