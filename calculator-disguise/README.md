# APK Customizer

Android app project for importing an APK, reading its package name and icon, and previewing a custom launcher label.

## Current test build
- Import an APK using Android's document picker.
- Inspect its embedded app label, package ID and icon.
- Edit the display name and preview the text change.
- Original APK is not modified or installed.

## Important
This initial build is an import/preview prototype. It does not yet rewrite APK manifests/resources or sign a modified APK. APKs that use split packages may require a complete split set. Repackaging also changes signing identity and can break updates or app integrity checks.

## Build
GitHub Actions workflow: `.github/workflows/build-calculator-apk.yml`.
