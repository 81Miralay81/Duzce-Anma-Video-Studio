# Android APK Build Verification

This branch exists to run the Android APK workflow on a pull request and verify that the current source compiles.

The CI job must:
- install Android API 35 and build tools 35.0.0
- compile the Kotlin Android application with Gradle 8.7 / Android Gradle Plugin 8.6.1
- publish `app/build/outputs/apk/debug/app-debug.apk` as the `Duzce-Anma-Studio-debug-apk` artifact

A successful CI build verifies compilation and APK packaging. It does not replace installation and export testing on a physical Android device.
