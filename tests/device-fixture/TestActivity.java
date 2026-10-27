package com.still.launcher.regression;

/** Disposable app used to verify launch tasks and install/update/uninstall invalidation. */
public final class TestActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Still test app");
        setContentView(text);
    }
}
