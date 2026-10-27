package com.still.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** System-bound only after user consent. No event subscription or content access. */
public final class StillGestureService extends AccessibilityService {
    private static StillGestureService connected;

    @Override protected void onServiceConnected() { connected = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(Intent intent) {
        if (connected == this) connected = null;
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }

    static boolean isConnected() { return connected != null; }
    static boolean request(boolean lock) {
        StillGestureService service = connected;
        if (service == null) return false;
        try {
            return service.performGlobalAction(lock ? GLOBAL_ACTION_LOCK_SCREEN : GLOBAL_ACTION_NOTIFICATIONS);
        } catch (IllegalStateException | SecurityException unavailable) {
            return false;
        }
    }
}
