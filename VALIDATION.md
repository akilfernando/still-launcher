# Validation

## Version 1.7 — 27 October 2026

- 27 Essentials selection/order/uninstall checks and 8 swipe-classification checks passed.
- Debug and release builds passed. Release lint reported no issues. The locally signed preview's APK signature verified successfully.
- Functional checks ran on a Samsung Galaxy S24. Minimum configured API level is 28; compatibility across all supported Android versions has not been established.
- Apps launch in independent Android tasks. A reproduced Instagram return-to-Home issue was fixed by adding `NEW_TASK | RESET_TASK_IF_NEEDED`; cold launching from Still then remained open.
- The app list is cached across foreground entries and invalidated by package changes. A disposable test app installed while Still was stopped appeared on return. Updating it preserved its Essential; permanently uninstalling it removed the Essential and drawer entry. The test app was removed afterward.
- Home dismissed an open settings sheet. Home gestures, Battery guide, About, and app-option sheets were visually inspected on the test device.
- Swipe up opened All apps. An interrupted touch followed by notification-shade dismissal restored Home's low-refresh preference. Android reported a settled 10 Hz on this device.
- Two short idle rendering snapshots showed no additional frames. This does not establish battery savings or long-duration stability.
- Source review covered all four production Java classes, lifecycle cleanup, app tasks, package-cache sequencing, permissions, and the optional accessibility service. A second reviewer examined launch and lifecycle edge cases and the resulting changes.

## Limits

No whole-phone energy savings, comparative battery-life percentage, long-duration stability result, complete TalkBack journey, work-profile support, Secure Folder support, large-font matrix, or comprehensive Android/device matrix is claimed.

The optional accessibility service is configured without event subscriptions, screen-content access, injected gestures, or polling. Its battery cost has not been measured.

The legacy ADB smoke and ordering scripts have updated selectors for the current icon UI, but were not rerun in full for version 1.7. Targeted device checks were performed directly using fresh UI reads. PowerShell syntax checks passed, and the disposable test-app builder compiled and signed successfully.

Private device logs, screenshots, local paths, credentials, and signing keys are not included in the repository.
