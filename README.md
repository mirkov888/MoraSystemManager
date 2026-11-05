# Mora System Manager

**Version 2.0.0 — Full System Control Center**

Android root utility app built with Kotlin and Jetpack Compose / Material You. Root is preferred; Shizuku is available as a fallback where its permission model supports the requested command.

## Implemented release milestones

- **V1.1 — Privacy Cat launcher icon:** supplied mascot artwork is the default launcher icon.
- **V1.2 — Adaptive & themed icons:** adaptive foreground/background plus a monochrome layer for supported launchers.
- **V1.3 — Battery & thermal monitor:** battery percentage/charging state and Android thermal status snapshot.
- **V1.4 — Advanced Debloater:** guarded disable action, protected package list, currently limited to third-party packages.
- **V1.5 — Kernel Manager:** kernel/ABI/fingerprint and SELinux diagnostics. Kernel flashing, frequency tuning, and boot-image modification are intentionally not included.
- **V2.0 — Full System Control Center:** runtime property editor with getprop snapshot backup, service inspection/start/stop with core-service guard, KernelSU/modules configuration archive backup/restore, and integrated diagnostics.

## Existing features

- Installed application inventory, search and filters.
- App details: package ID, version, UID, APK path, enabled state.
- Enable/disable and uninstall user apps for Android user 0.
- System app uninstall guard and package inventory export.
- Root preferred; Shizuku fallback.
- Switchable launcher icon variants in Settings.

## Requirements

- Android 8.0+ (minSdk 26)
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.7.3

## Build

Open in Android Studio, sync Gradle, then run `assembleDebug`.
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions is configured at `.github/workflows/android-build.yml` and builds debug and unsigned release APKs on pushes to `main`/`master`, version tags (`v*`), or manual dispatch. Artifacts are attached to the Actions run.

## Safety and limitations

- Build.prop editing is implemented as a **runtime property editor** using `resetprop -p`, restricted to `persist.*` and `debug.*`; it does not remount or directly edit read-only `/system/build.prop`.
- Thermal information is a framework snapshot, not a continuous temperature graph or battery-health estimate. Sensor availability depends on the device/vendor.
- The advanced debloater currently disables third-party packages only. System-app removal is not offered by this screen.
- Service operations are inherently risky even with a denylist. Avoid stopping unknown services; core Android services are blocked.
- Backup archives cover `/data/adb/ksu` and `/data/adb/modules` when those paths exist. Restore overwrites those paths and can cause bootloops if modules/configuration are incompatible. Keep a recovery route available.
- Shizuku may not have permission to execute every command; full functionality generally requires KernelSU/Magisk root.
- This source has **not yet been compiled or tested on-device**. Build with GitHub Actions and test on a spare/test device before relying on restore or service controls.


### Build-log fixes (November 2026)
- Removed an accidentally duplicated package/import block in `ControlCenterScreen.kt` that caused cascading Kotlin parser errors.
- Escaped shell variables in Kotlin command strings (`$stamp`, `$archive`, `$file`) so Kotlin does not interpret them as Kotlin identifiers.
- Replaced the compile-time call to `Shizuku.newProcess` with a reflective compatibility bridge because Shizuku API 13.1.5 exposes that legacy method as private to Kotlin. Shizuku's own changelog recommends migrating to `UserService`; the bridge is transitional and should be replaced with a proper UserService implementation in a later maintenance update.
