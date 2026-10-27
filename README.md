# Still Launcher

A small, open-source Android launcher that does less. A black canvas, a clock, up to three Essentials, and a searchable app list.

Built with native Android views and no third-party runtime libraries. Designed and tested on a Samsung Galaxy S24; Android 9 or newer is supported by the build configuration. Other devices need testing.

## Features

- Up to three Essentials, with saved ordering and no app icons on Home.
- Alphabetical All apps list with local search and stars to manage Essentials.
- Swipe up for All apps, swipe down for notifications, and double-tap empty space to lock.
- Lightweight native sheets for settings and app actions.
- Home requests the lowest refresh rate advertised at the current resolution. All apps uses Android's adaptive policy for smooth scrolling.

Long-press an Essential to move it up or down. Long-press an app in All apps to add/remove it or open Android's app information page. Tap the Still logo at the top left for settings and to choose Still as the default launcher.

## Battery and privacy

Still has no Internet permission, ads, analytics, wallpaper rendering, widgets, sensors, wake locks, scheduled jobs, or background app polling. App labels load off the UI thread and are cached between app launches. Package changes invalidate that cache; querying waits until the launcher is visible. Preferences stay on the device and are excluded from backup and transfer.

The clock shows minutes rather than seconds. Home's refresh-rate preference is released during touch interaction, while another window has focus, and during app transitions. A single cancellable callback restores the idle preference after 800 ms. The S24 used for testing settled at an Android-reported 10 Hz; the operating system has final control, and this is not an external measurement of panel behavior.

**Whole-phone battery savings have not been measured.** Screen brightness, cellular reception, other apps, and usage patterns can dominate battery consumption.

## Optional gesture permission

Swipe up works without special access. Notifications and locking require the optional **Still Gestures** accessibility service:

1. Tap the **Still logo → Home gestures → Accessibility settings**.
2. Open **Installed apps → Still Gestures** and enable it.

The service only performs notification/lock global actions in response to Home gestures. It has no accessibility-event subscriptions, screen-content retrieval, gesture injection, network access, or polling. Android keeps it bound while enabled, so it has some overhead; its battery impact has not been measured. Disable it in Android Accessibility settings to turn those two gestures off. No device-admin permission is requested.

## Build and install

Requirements: JDK 17, Android SDK platform 35, Build Tools 34.0.0, and Internet access for the first dependency download. Gradle 8.9 and Android Gradle Plugin 8.7.0 are pinned. Configure `sdk.dir` in an untracked `local.properties`, or configure your Android SDK environment.

```sh
./gradlew assembleDebug assembleRelease lintRelease
```

On Windows, use `gradlew.bat`. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it with Android's package installer, or:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Choose Still under **Settings → Apps → Default apps → Home app**. Names vary by device. To switch back, select your previous launcher there.

Debug builds use a development certificate. Release builds are unsigned until you sign them. Keep signing keys private and use the same key for future updates. This repository contains no signing keys or prebuilt APKs.

The optional Windows helper `scripts/build-preview.ps1` runs the policy checks, builds/lints the app, and signs a local preview with the standard local Android debug keystore. It works around an observed Windows JDK socket-path issue using a project-local temporary directory. It is for local development, not production signing.

## Tests and contributions

The policy checks use plain Java and need no test framework:

```sh
mkdir -p build/policy-tests
javac -d build/policy-tests app/src/main/java/com/still/launcher/Essentials.java tests/EssentialsTest.java app/src/main/java/com/still/launcher/GestureRules.java tests/GestureRulesTest.java
java -cp build/policy-tests com.still.launcher.EssentialsTest
java -cp build/policy-tests com.still.launcher.GestureRulesTest
```

See [VALIDATION.md](VALIDATION.md) for completed checks and limitations. Optional ADB scripts under `scripts/` interact with a connected device; read them first and prefer an emulator. The smoke script temporarily changes the default launcher and adds Settings as an Essential. The ordering script temporarily changes Essentials and restores them. `build-test-fixture.ps1` builds a disposable app for manual package-change and app-task tests; it is not included in Still's APK.

Issues and pull requests are welcome. Keep changes focused, preserve the minimal UI, avoid continuous background work, and explain validation. Please do not post account details, device identifiers, private logs, or signing material in issues.

## Current scope

Still lists apps in the current personal profile. Work profiles, Samsung Secure Folder, widgets, icon packs, notification badges, and Samsung-specific Recents animations are not implemented. UI copy is English. Samsung and Android retain control over system navigation, display policy, and battery settings.

## License

Still is licensed under the [MIT License](LICENSE). Gradle Wrapper notices are preserved separately; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
