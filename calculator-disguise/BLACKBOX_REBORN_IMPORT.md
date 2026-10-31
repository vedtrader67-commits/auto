# BlackBox Reborn integration

This branch replaces the incorrect VirtualXposed experiment with the selected BlackBox Reborn engine.

- Engine: https://github.com/fdavids77/BlackBoxReborn
- Pinned upstream commit: 7b86a9d62a974650e0659825758931cd91140827
- Engine core module: Bcore
- Upstream host app: app
- License: GPL-3.0
- Android build requirements from upstream: JDK 17, Gradle 8.14.5, Android Gradle Plugin 8.13.2, compile SDK 36, NDK 27.0.12077973.
- Upstream app targets API 28 and supports ARMv7/ARM64.

## Important integration status

The engine is pinned as a Git submodule for reproducible builds. This is not yet a finished Calculator-hosted virtual environment. BlackBox is a framework with a required Application bootstrap, manifest-declared proxy components, native libraries, and lifecycle wiring. Simply adding Bcore as a dependency would not make APK installation or guest launching work. The Calculator app still needs that host integration and an Android 12 device test.

The Calculator project remains the host target. Do not replace it with the standalone BlackBox launcher or claim guest apps run until the complete integration is built and tested.


## Calculator host build

The integration build applies `scripts/prepare-blackbox.sh` to the pinned engine source in CI. It adds the Calculator gate activity and calculator icon/label, launches BlackBox `WelcomeActivity` only after the user enters `6767` and presses `=`, then builds the BlackBox host APK with application ID `com.calculator.simple`. The APK is published as a prerelease asset by the `Build Calculator with BlackBox Reborn` workflow.

Android SDK CI uses the current setup-android action and installs API 36 / NDK 27 for the pinned build.
