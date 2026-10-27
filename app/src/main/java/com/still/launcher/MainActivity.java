package com.still.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Event-driven launcher; optional accessibility service performs user-requested actions only. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(232, 234, 229);
    private static final int MUTED = Color.rgb(153, 161, 151);
    private static final int ACCENT = Color.rgb(185, 214, 160);
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<App> apps = new ArrayList<>();
    private final List<App> visible = new ArrayList<>();
    private Set<String> favorites;
    private List<String> favoriteOrder = new ArrayList<>();
    private SharedPreferences preferences;
    private AppAdapter adapter;
    private TextView battery, section, empty;
    private TextClock clock, date;
    private EditText search;
    private android.widget.ImageButton switcher;
    private LinearLayout root, header;
    private android.widget.FrameLayout content;
    private boolean allApps, started, loading, appsDirty = true;
    private android.app.Dialog activeSheet;
    private GestureDetector homeGestures;
    private ListView appList;
    private android.graphics.drawable.Drawable appListSelector;
    private boolean consumingGesture, multiplePointers, blankTap;
    private Runnable pendingGesture;
    private boolean resumed, interacting;
    private final int[] viewLocation = new int[2];
    private final Runnable settleRefresh = () -> {
        interacting = false;
        applyRefreshPolicy();
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            String text = level < 0 || scale <= 0 ? "Battery" :
                Math.round(level * 100f / scale) + "%  ·  " +
                (status == BatteryManager.BATTERY_STATUS_FULL ? "Full" : plugged ? "Plugged in" : "On battery");
            if (!text.contentEquals(battery.getText())) battery.setText(text);
        }
    };
    private final BroadcastReceiver packagesReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // A replacement temporarily removes a package; keep its Essentials.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && intent.getData() != null) {
                String removed = intent.getData().getSchemeSpecificPart();
                if (Essentials.removePackage(favorites, favoriteOrder, removed)) saveFavorites();
            }
            appsDirty = true;
            loadApps();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("still", MODE_PRIVATE);
        favorites = new HashSet<>(preferences.getStringSet("favorites", new HashSet<>()));
        try {
            org.json.JSONArray saved = new org.json.JSONArray(preferences.getString("favorites_order", "[]"));
            for (int i = 0; i < saved.length(); i++) favoriteOrder.add(saved.getString(i));
        } catch (org.json.JSONException invalid) {
            favoriteOrder.clear(); // Existing selections still migrate in their alphabetical order.
        }
        if (favorites.size() > Essentials.LIMIT) {
            Set<String> previous = new HashSet<>(favorites);
            favorites = Essentials.migrate(favorites);
            preferences.edit().putStringSet("favorites_before_limit", previous)
                .putStringSet("favorites", new HashSet<>(favorites)).apply();
        }
        root = column();
        root.setBackgroundColor(Color.BLACK);
        root.setClipToPadding(false);
        root.setFocusableInTouchMode(true);
        root.setPadding(dp(24), dp(12), dp(24), dp(8));
        setContentView(root);
        configureInsets();
        configureGestures();

        LinearLayout top = row();
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        android.widget.ImageButton brand = new android.widget.ImageButton(this);
        brand.setImageResource(R.drawable.ic_still_mark);
        brand.setContentDescription("Still settings");
        brand.setBackgroundColor(Color.BLACK);
        brand.setPadding(dp(20), dp(10), 0, dp(10));
        brand.setOnClickListener(v -> showSettings());
        top.addView(brand, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        header = column();
        clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        boolean compact = getResources().getConfiguration().screenHeightDp < 500;
        clock.setTextSize(compact ? 32 : 64);
        clock.setTextColor(INK);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(clock);
        date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextSize(16);
        date.setTextColor(MUTED);
        header.addView(date);
        TextWatcher clockAlignment = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable text) { alignClockInk(); }
        };
        clock.addTextChangedListener(clockAlignment);
        date.addTextChangedListener(clockAlignment);
        alignClockInk();
        if (compact) date.setVisibility(View.GONE);
        battery = label("Battery", 14, ACCENT);
        battery.setPadding(0, dp(compact ? 0 : 14), 0, dp(compact ? 0 : 22));
        battery.setMinHeight(dp(48));
        battery.setContentDescription("Open battery settings");
        battery.setOnClickListener(v -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        header.addView(battery);
        root.addView(header);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(18);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("Search apps");
        search.setContentDescription("Search installed apps");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        search.setMinHeight(dp(56));
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(); }
            public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (visible.size() == 1) { launch(visible.get(0)); return true; }
            return false;
        });
        root.addView(search);
        section = label("YOUR ESSENTIALS", 12, MUTED);
        section.setLetterSpacing(.12f);
        section.setPadding(0, dp(14), 0, dp(10));
        root.addView(section);

        content = new android.widget.FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        extendAppRowsToEdge();
        ListView list = new ListView(this);
        appList = list;
        appListSelector = list.getSelector();
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDivider(null);
        list.setCacheColorHint(Color.BLACK);
        list.setVerticalScrollBarEnabled(false);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> launch(visible.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            appOptions(visible.get(position)); return true;
        });
        content.addView(list, new android.widget.FrameLayout.LayoutParams(-1, -1));
        empty = label("Loading apps…", 17, MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);

        switcher = new android.widget.ImageButton(this);
        switcher.setImageResource(R.drawable.ic_arrow_up);
        switcher.setBackgroundColor(Color.BLACK);
        switcher.setContentDescription("All apps");
        switcher.setOnClickListener(v -> setAllApps(!allApps));
        root.addView(switcher, new LinearLayout.LayoutParams(-1, dp(56)));
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> setAllApps(false));
        }
        // Keep invalidation alive while stopped, but only do package work when visible.
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packagesReceiver, packages);
        IntentFilter availability = new IntentFilter();
        availability.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        availability.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        availability.addAction(Intent.ACTION_PACKAGES_SUSPENDED);
        availability.addAction(Intent.ACTION_PACKAGES_UNSUSPENDED);
        registerReceiver(packagesReceiver, availability);
    }

    private void configureGestures() {
        homeGestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (first == null || multiplePointers) return false;
                int direction = GestureRules.swipe(current.getX() - first.getX(),
                    current.getY() - first.getY(), dp(80), current.getEventTime() - first.getEventTime(), multiplePointers);
                if (direction == GestureRules.NONE) return false;
                pendingGesture = direction == GestureRules.UP ? () -> setAllApps(true) : () -> globalGesture(false);
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent first) {
                if (!blankTap || multiplePointers || interactiveHomePoint(first.getX(), first.getY())) return false;
                pendingGesture = () -> globalGesture(true);
                return true;
            }
        });
        // Essential rows retain their own long-press reorder menu.
        homeGestures.setIsLongpressEnabled(false);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (!allApps || interacting) {
            if (action == MotionEvent.ACTION_DOWN) {
                root.removeCallbacks(settleRefresh);
                interacting = true;
                applyRefreshPolicy();
            } else if (resumed && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
                root.removeCallbacks(settleRefresh);
                root.postDelayed(settleRefresh, 800);
            }
        }
        if (consumingGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) consumingGesture = false;
            return true;
        }
        if (homeGestures == null || allApps) return super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN) {
            multiplePointers = false;
            blankTap = !interactiveHomePoint(event.getX(), event.getY());
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) multiplePointers = true;
        pendingGesture = null;
        if (multiplePointers) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            homeGestures.onTouchEvent(cancel);
            cancel.recycle();
        } else homeGestures.onTouchEvent(event);
        if (pendingGesture != null) {
            Runnable perform = pendingGesture;
            pendingGesture = null;
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            homeGestures.onTouchEvent(cancel);
            cancel.recycle();
            consumingGesture = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
            perform.run();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean containsPoint(View view, float x, float y) {
        if (view.getVisibility() != View.VISIBLE) return false;
        view.getLocationInWindow(viewLocation);
        return x >= viewLocation[0] && x < viewLocation[0] + view.getWidth()
            && y >= viewLocation[1] && y < viewLocation[1] + view.getHeight();
    }

    private boolean interactiveHomePoint(float x, float y) {
        if (appList != null) {
            for (int i = 0; i < appList.getChildCount(); i++)
                if (containsPoint(appList.getChildAt(i), x, y)) return true;
        }
        return clickablePoint(root, x, y);
    }

    private boolean clickablePoint(View view, float x, float y) {
        if (!containsPoint(view, x, y)) return false;
        if (view != appList && view.isClickable()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (clickablePoint(group.getChildAt(i), x, y)) return true;
        }
        return false;
    }

    private void globalGesture(boolean lock) {
        if (StillGestureService.request(lock)) return;
        if (StillGestureService.isConnected()) {
            android.widget.Toast.makeText(this, "Android could not complete that action. Try again.",
                android.widget.Toast.LENGTH_SHORT).show();
        } else showGestureSettings();
    }

    private void showGestureSettings() {
        showMessage("Home gestures",
            "Swipe up: All apps\nSwipe down: Notifications\nDouble-tap empty space: Lock screen\n\n" +
                "Swipe down and locking need the optional Still Gestures accessibility service. " +
                "It only performs these actions when you gesture on Home. It cannot read screen content, " +
                "does not subscribe to accessibility events, and has no network access or polling. " +
                "Android keeps the service connected while enabled.\n\n" +
                (StillGestureService.isConnected() ? "Still Gestures is enabled." :
                "In Accessibility settings, open Installed apps → Still Gestures and enable it."),
            "Accessibility settings", () -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void configureInsets() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(safe.left + dp(24), safe.top + dp(8), safe.right + dp(24), safe.bottom + dp(8));
                extendAppRowsToEdge();
                return insets;
            });
            getWindow().getInsetsController().setSystemBarsAppearance(0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        applyRefreshPolicy();
    }

    @Override protected void onPause() {
        resumed = false;
        interacting = false;
        root.removeCallbacks(settleRefresh);
        applyRefreshPolicy();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (root == null) return;
        if (!focused) {
            interacting = false;
            root.removeCallbacks(settleRefresh);
            cancelHomeGesture();
        }
        applyRefreshPolicy();
    }

    @Override protected void onStart() {
        super.onStart();
        applyRefreshPolicy();
        started = true;
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        filterApps();
        loadApps();
    }

    @Override protected void onStop() {
        dismissSheet();
        cancelHomeGesture();
        started = false;
        unregisterReceiver(batteryReceiver);
        super.onStop();
    }

    private void cancelHomeGesture() {
        consumingGesture = false;
        multiplePointers = false;
        pendingGesture = null;
        if (homeGestures != null) {
            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            homeGestures.onTouchEvent(cancel);
            cancel.recycle();
        }
    }

    @Override protected void onDestroy() {
        dismissSheet();
        root.removeCallbacks(settleRefresh);
        unregisterReceiver(packagesReceiver);
        loader.shutdownNow();
        super.onDestroy();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            dismissSheet();
            cancelHomeGesture();
            setAllApps(false);
        }
    }
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { setAllApps(false); }

    private void loadApps() {
        if (!started || !appsDirty || loading) return;
        appsDirty = false;
        loading = true;
        loader.execute(() -> {
            List<App> found = new ArrayList<>();
            try {
                Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo info : getPackageManager().queryIntentActivities(query, 0)) {
                    if (info.activityInfo.packageName.equals(getPackageName())) continue;
                    found.add(new App(info.loadLabel(getPackageManager()).toString(),
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name)));
                }
                Collator collator = Collator.getInstance();
                found.sort((a, b) -> collator.compare(a.name, b.name));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    apps.clear(); apps.addAll(found); loading = false;
                    List<String> normalized = Essentials.order(favorites, favoriteOrder, appKeys());
                    if (!normalized.equals(favoriteOrder)) {
                        favoriteOrder = normalized;
                        saveFavorites();
                    }
                    if (started) filterApps();
                    loadApps(); // A package may have changed during the query.
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading = false;
                    appsDirty = true; // Retry next foreground entry, not in a loop.
                    if (started && apps.isEmpty()) empty.setText(R.string.load_error);
                });
            }
        });
    }

    private void setAllApps(boolean show) {
        allApps = show;
        appList.setSelector(show ? appListSelector
            : new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        applyRefreshPolicy();
        header.setVisibility(show ? View.GONE : View.VISIBLE);
        search.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean hadQuery = search.length() != 0;
        if (hadQuery) search.setText(""); // The watcher performs the single filter pass.
        root.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(search.getWindowToken(), 0);
        switcher.setImageResource(show ? R.drawable.ic_home : R.drawable.ic_arrow_up);
        switcher.setContentDescription(show ? "Home" : "All apps");
        if (!hadQuery) filterApps();
    }

    /** A seamless preference, not a system-wide mode lock. Samsung retains final control. */
    @SuppressWarnings("deprecation")
    private void applyRefreshPolicy() {
        float rate = 0f; // Let Android boost scrolling and idle naturally in All apps.
        android.view.Display display = getWindowManager().getDefaultDisplay();
        if (!allApps && resumed && hasWindowFocus() && !interacting && display != null) {
            android.view.Display.Mode current = display.getMode();
            for (android.view.Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() > 0f
                        && (rate == 0f || mode.getRefreshRate() < rate)) {
                    rate = mode.getRefreshRate();
                }
            }
        }
        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.preferredRefreshRate != rate) {
            params.preferredRefreshRate = rate;
            getWindow().setAttributes(params);
        }
    }

    private void filterApps() {
        if (adapter == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (App app : apps) {
            if ((allApps || favorites.contains(app.key()))
                && app.searchName.contains(query)) visible.add(app);
        }
        if (!allApps) visible.sort((a, b) -> Integer.compare(
            favoriteOrder.indexOf(a.key()), favoriteOrder.indexOf(b.key())));
        section.setText(allApps ? "ALL APPS  ·  " + visible.size() : "YOUR ESSENTIALS");
        empty.setText(allApps ? (query.isEmpty() ? "No launchable apps found." : "No matching apps.")
            : "A little less on your home screen.\n\nOpen All apps and star up to three\nessentials to keep here.");
        adapter.notifyDataSetChanged();
    }

    private void toggleFavorite(App app) {
        if (!Essentials.toggle(favorites, app.key())) {
            android.widget.Toast.makeText(this,
                "Choose up to 3 essentials. Remove a star in All apps to add another.",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        favoriteOrder = Essentials.order(favorites, favoriteOrder, appKeys());
        saveFavorites();
        filterApps();
    }

    private List<String> appKeys() {
        List<String> keys = new ArrayList<>();
        for (App app : apps) keys.add(app.key());
        return keys;
    }

    private void saveFavorites() {
        preferences.edit().putStringSet("favorites", new HashSet<>(favorites))
            .putString("favorites_order", new org.json.JSONArray(favoriteOrder).toString()).apply();
    }

    private void launch(App app) {
        try {
            Intent intent = Intent.makeMainActivity(app.component);
            // makeMainActivity does not set NEW_TASK. Apps must launch in their
            // own task; Instagram rejects its entry activity inside Still's task.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            setAllApps(false);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            showMessage("App unavailable", "This app may be disabled or removed.", null, null);
            appsDirty = true;
            loadApps();
        }
    }

    private void appOptions(App app) {
        if (!allApps) {
            List<App> home = new ArrayList<>(visible);
            int position = home.indexOf(app);
            List<String> choices = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            if (position > 0) {
                choices.add("Move up");
                actions.add(() -> moveEssential(app, home.get(position - 1)));
            }
            if (position >= 0 && position < home.size() - 1) {
                choices.add("Move down");
                actions.add(() -> moveEssential(app, home.get(position + 1)));
            }
            choices.add("App info");
            actions.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.component.getPackageName()))));
            showChoices(app.name, choices, actions);
            return;
        }
        showChoices(app.name,
            java.util.Arrays.asList(favorites.contains(app.key()) ? "Remove from home" : "Add to home", "App info"),
            java.util.Arrays.asList(() -> toggleFavorite(app),
                () -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + app.component.getPackageName())))));
    }

    private void moveEssential(App app, App neighbor) {
        if (Essentials.swap(favoriteOrder, app.key(), neighbor.key())) {
            saveFavorites();
            filterApps();
        }
    }

    private void showSettings() {
        Sheet sheet = new Sheet("Settings");
        menuSection(sheet.panel, "LAUNCHER");
        menuItem(sheet.panel, sheet.dialog, "Default launcher", "Choose your home screen", this::requestHome);
        menuItem(sheet.panel, sheet.dialog, "Home gestures", "Swipe, notifications & locking", this::showGestureSettings);
        menuSection(sheet.panel, "PHONE");
        menuItem(sheet.panel, sheet.dialog, "Battery settings", "Power saving & battery usage",
            () -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        menuItem(sheet.panel, sheet.dialog, "Display settings", "Brightness & motion smoothness",
            () -> openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        menuItem(sheet.panel, sheet.dialog, "Battery guide", "Small changes for your Galaxy", this::showGuide);
        menuItem(sheet.panel, sheet.dialog, "About Still", "A quiet place to begin", this::showAbout);
        sheet.show();
    }

    /** Shared, on-demand native surface for every Still-owned dialog. */
    private void dismissSheet() {
        if (activeSheet != null) activeSheet.dismiss();
    }

    private final class Sheet {
        final android.app.Dialog dialog = new android.app.Dialog(MainActivity.this);
        final LinearLayout panel = column();

        Sheet(String name) {
        dismissSheet();
        activeSheet = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (activeSheet == dialog) activeSheet = null;
        });
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setColor(Color.rgb(17, 20, 17));
        surface.setCornerRadius(dp(24));
        surface.setStroke(dp(1), Color.rgb(43, 49, 42));
        panel.setBackground(surface);

        LinearLayout heading = row();
        TextView title = label(name, 22, INK);
        title.setPadding(dp(12), 0, 0, 0);
        title.setAccessibilityHeading(true);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(60), 1));
        Button close = button("×");
        close.setTextColor(MUTED);
        close.setTextSize(24);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription("Close " + name);
        close.setOnClickListener(v -> dialog.dismiss());
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(heading);
        }

        void show() {
        if (isFinishing() || isDestroyed()) { dismissSheet(); return; }
        android.widget.ScrollView scroll = new android.widget.ScrollView(MainActivity.this);
        scroll.setFillViewport(false);
        scroll.addView(panel);
        scroll.setVerticalScrollBarEnabled(false);
        dialog.setContentView(scroll);
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.5f);
        window.setWindowAnimations(0);
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(420));
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxHeight = root.getHeight() - dp(32);
        window.setLayout(width, Math.min(panel.getMeasuredHeight(), Math.max(dp(160), maxHeight)));
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        params.y = dp(16);
        window.setAttributes(params);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        }
    }

    private void showChoices(String title, List<String> choices, List<Runnable> actions) {
        Sheet sheet = new Sheet(title);
        for (int i = 0; i < choices.size(); i++)
            menuItem(sheet.panel, sheet.dialog, choices.get(i), null, actions.get(i));
        sheet.show();
    }

    private void showMessage(String title, String message, String actionLabel, Runnable action) {
        Sheet sheet = new Sheet(title);
        TextView body = label(message, 15, MUTED);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(dp(12), dp(4), dp(12), dp(20));
        sheet.panel.addView(body);
        if (action != null) menuItem(sheet.panel, sheet.dialog, actionLabel, null, action);
        sheet.show();
    }

    private void menuSection(LinearLayout panel, String text) {
        TextView label = label(text, 10, MUTED);
        label.setLetterSpacing(.14f);
        label.setPadding(dp(12), dp(12), dp(12), dp(6));
        label.setAccessibilityHeading(true);
        panel.addView(label);
    }

    private void menuItem(LinearLayout panel, android.app.Dialog dialog, String title,
            String detail, Runnable action) {
        LinearLayout item = column();
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setMinimumHeight(dp(60));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.addView(label(title, 16, INK));
        if (detail != null) item.addView(label(detail, 12, MUTED));
        item.setContentDescription(detail == null ? title : title + ". " + detail);
        item.setFocusable(true);
        item.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x18B9D6A0), null,
            new android.graphics.drawable.ColorDrawable(Color.WHITE)));
        item.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        panel.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showAbout() {
        showMessage("About Still",
            "A quiet place to begin.\n\nBlack canvas. Text-only apps. No ads, trackers, network permission, widgets, or background polling. Optional Still Gestures accessibility service enables notifications and locking.\n\nFavorites stay on this device. Long-press a Home app to reorder it.\n\nThis version lists apps in your current personal profile. Work profiles, Samsung Secure Folder contents, widgets, and Samsung Recents integration are not implemented.\n\nBattery savings vary; no measured savings are claimed.", null, null);
    }

    private void requestHome() {
        // A role request requires a result-launch flow and may be suppressed.
        // The system chooser also works when Still is already the default.
        for (String action : new String[]{Settings.ACTION_HOME_SETTINGS,
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS}) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                // Some manufacturers expose only the general default-app page.
            }
        }
        showMessage("Choose Still Launcher",
            "Open Settings → Apps → Choose default apps → Home app, then select Still Launcher.",
            "Open settings", () -> openSettings(Settings.ACTION_SETTINGS));
    }

    private void showGuide() {
        showMessage("Battery guide",
            "In Samsung Settings:\n\n• Battery → Power saving: use when endurance matters most.\n• Device care → Performance profile → Light: prioritize battery life for everyday use.\n• Display → Motion smoothness → Standard: an optional 60 Hz tradeoff.\n• Use a short screen timeout and comfortable brightness.\n\nNames vary with One UI. Still opens system settings; it cannot change these controls itself. Black pixels are useful on the S24 AMOLED display, but total savings depend on screen time and other apps.\n\nHome/Recents gestures remain controlled by Samsung. To switch back: Settings → Apps → Choose default apps → Home app → One UI Home.",
            "Open settings", () -> openSettings(Settings.ACTION_SETTINGS));
    }

    private void openSettings(String action) { safeStart(new Intent(action)); }
    private void safeStart(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException error) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                showMessage("Open Settings", "Open your phone's Settings app to continue.", null, null);
            }
        }
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private Button button(String text) {
        Button view = new Button(this); view.setText(text); view.setTextSize(15);
        view.setTextColor(INK); view.setAllCaps(false); view.setMinHeight(dp(48));
        view.setMinimumWidth(dp(48)); view.setBackgroundColor(Color.BLACK); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void extendAppRowsToEdge() {
        if (content == null) return;
        LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) content.getLayoutParams();
        int margin = -root.getPaddingRight();
        if (layout.rightMargin != margin) {
            layout.rightMargin = margin;
            content.setLayoutParams(layout);
        }
    }

    private void alignClockInk() {
        // Equal view bounds do not align the visible glyphs at different font sizes.
        // Run only when the system clocks change text; no extra timer or layout loop.
        if (clock.length() == 0 || date.length() == 0) return;
        android.graphics.Rect ink = new android.graphics.Rect();
        clock.getPaint().getTextBounds(clock.getText().toString(), 0, 1, ink);
        int clockInset = ink.left;
        date.getPaint().getTextBounds(date.getText().toString(), 0, 1, ink);
        clock.setTranslationX(ink.left - clockInset);
    }

    private static final class App {
        final String name, searchName, componentKey;
        final ComponentName component;
        App(String name, ComponentName component) {
            this.name = name;
            this.searchName = name.toLowerCase(Locale.ROOT);
            this.component = component;
            this.componentKey = component.flattenToString();
        }
        String key() { return componentKey; }
    }

    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public App getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout view;
            if (convertView == null) {
                view = row(); view.setMinimumHeight(dp(64));
                TextView name = label("", 23, INK);
                name.setPadding(0, dp(12), dp(8), dp(12));
                view.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                Button star = button("☆");
                star.setTextSize(24); star.setTextColor(ACCENT); star.setFocusable(false);
                view.addView(star, new LinearLayout.LayoutParams(dp(56), dp(56)));
            } else view = (LinearLayout) convertView;
            App app = getItem(position);
            // Extend the row background to the screen edge, keeping text/stars inset.
            view.setPadding(0, 0, root.getPaddingRight(), 0);
            if (allApps) view.setBackground(null);
            else view.setBackgroundResource(position == getCount() - 1
                ? R.drawable.essential_bounds_last : R.drawable.essential_bounds);
            // Home rows use View's click dispatch instead of AbsListView's delayed
            // pressed-state click. Launch on release; keep cancellation and long press.
            view.setOnClickListener(allApps ? null : v -> launch(app));
            view.setOnLongClickListener(allApps ? null : v -> { appOptions(app); return true; });
            view.setClickable(!allApps);
            view.setLongClickable(!allApps);
            ((TextView) view.getChildAt(0)).setText(app.name);
            Button star = (Button) view.getChildAt(1);
            star.setVisibility(allApps ? View.VISIBLE : View.GONE);
            boolean favorite = favorites.contains(app.key());
            star.setText(favorite ? "★" : "☆");
            star.setContentDescription((favorite ? "Remove " : "Add ") + app.name + (favorite ? " from home" : " to home"));
            star.setOnClickListener(v -> toggleFavorite(app));
            return view;
        }
    }
}
