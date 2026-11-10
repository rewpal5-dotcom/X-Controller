package com.xcontroller.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Home screen — status + saare modules. */
public class MainActivity extends Activity {

    private ProfileStore store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new ProfileStore(this);
        store.ensureDefault();
        build();
    }

    @Override
    protected void onResume() { super.onResume(); build(); }

    private void build() {
        LinearLayout box = UiKit.column(this);

        TextView logo = UiKit.title(this, "X CONTROLLER", 28);
        logo.setTextColor(UiKit.ACCENT);
        box.addView(logo);
        box.addView(UiKit.muted(this, "Universal Keyboard & Mouse Gaming"));

        /* ---- status card ---- */
        LinearLayout st = UiKit.card(this);
        st.addView(UiKit.title(this, "Status", 15));
        st.addView(statusLine("Keyboard", hasKeyboard(), "OTG / Bluetooth keyboard jodein"));
        st.addView(statusLine("Mouse", hasMouse(), "OTG / Bluetooth mouse jodein"));
        st.addView(statusLine("Controller service", OverlayService.running, "Start Controller dabayein"));
        st.addView(statusLine("Touch mapping (Accessibility)",
                KeyAccessibilityService.isRunning(), "Settings se ON karein"));
        st.addView(statusLine("Floating permission", canOverlay(), "Permission dein"));
        box.addView(st);

        /* ---- primary ---- */
        Button start = UiKit.button(this, "▶  Start Controller", UiKit.ACCENT, 0xFF04121A);
        start.setOnClickListener(v -> startController());
        box.addView(start, UiKit.matchWrap(10));

        box.addView(tile("🎮  My Games", "Game ke hisaab se profile", GamesActivity.class));
        box.addView(tile("⌨  Keyboard & Mouse", "Detection aur test screens",
                null, v -> startActivity(new Intent(this, TestActivity.class)
                        .putExtra("mode", "keyboard"))));
        box.addView(tile("🎛  Custom Controls", "Key mapping editor", MappingActivity.class));
        box.addView(tile("📐  Controller Profiles", "Layout, size, position", LayoutActivity.class));
        box.addView(tile("🧪  Compatibility Test", "Gesture kaam kar rahi hai ya block ho rahi hai", DiagActivity.class));
        box.addView(tile("⚙  Settings", "Sensitivity, theme, reset", SettingsActivity.class));
        box.addView(tile("❓  Help & Support", "Kaise chalayein", AboutActivity.class));
        box.addView(tile("ℹ  About App", "Privacy, terms, compatibility",
                null, v -> startActivity(new Intent(this, AboutActivity.class).putExtra("tab", "about"))));

        /* ---- quick toggles ---- */
        LinearLayout quick = UiKit.card(this);
        quick.addView(UiKit.title(this, "Input mode", 15));

        ProfileStore.Profile activeProf = store.active();
        if (activeProf != null) {
            android.widget.Switch kb = new android.widget.Switch(this);
            kb.setText("Keyboard capture (keys → game)");
            kb.setTextColor(UiKit.TEXT);
            kb.setChecked(activeProf.keyCapture);
            kb.setOnCheckedChangeListener((v, on) -> {
                ProfileStore.Profile p2 = store.active();
                if (p2 != null) { p2.keyCapture = on; store.upsert(p2); reloadOverlay(); }
            });
            quick.addView(kb, UiKit.matchWrap(10));

            android.widget.Switch ms = new android.widget.Switch(this);
            ms.setText("Mouse mode (aim + clicks)");
            ms.setTextColor(UiKit.TEXT);
            ms.setChecked(activeProf.aimEnabled);
            ms.setOnCheckedChangeListener((v, on) -> {
                ProfileStore.Profile p2 = store.active();
                if (p2 != null) { p2.aimEnabled = on; store.upsert(p2); reloadOverlay(); }
            });
            quick.addView(ms, UiKit.matchWrap(6));
            quick.addView(UiKit.muted(this,
                    "Badlav agle baar Start Controller karne par lagu honge."));
        }
        box.addView(quick);

        LinearLayout tip = UiKit.card(this);
        tip.addView(UiKit.title(this, "Floating button", 15));
        tip.addView(UiKit.muted(this,
                "Game ke upar chhota 'X' button dikhega.\n"
              + "Tap karein → EDIT mode: saare controls dikhte hain, jahan chahein drag karein.\n"
              + "Khaali jagah par tap → naya button. Button par tap → select → size / key / delete.\n"
              + "✓ Done dabayein → PLAY mode."));
        box.addView(tip);

        ProfileStore.Profile p = store.active();
        TextView act = UiKit.muted(this, "Active profile: " + (p == null ? "—" : p.name)
                + "  ·  " + (p == null ? 0 : p.bindings.size()) + " controls");
        act.setPadding(0, 20, 0, 0);
        box.addView(act);

        ScrollView sv = UiKit.scroll(this, box);
        setContentView(sv);
    }

    private LinearLayout statusLine(String name, boolean ok, String hint) {
        LinearLayout row = UiKit.row(this);
        row.setPadding(0, 14, 0, 0);
        TextView n = UiKit.body(this, (ok ? "✅  " : "⚠️  ") + name, 13.5f,
                ok ? UiKit.GREEN : UiKit.AMBER);
        n.setLayoutParams(UiKit.weight(1f));
        row.addView(n);
        if (!ok) row.addView(UiKit.muted(this, hint));
        return row;
    }

    private LinearLayout tile(String label, String sub, Class<?> target) {
        return tile(label, sub, target, null);
    }

    private LinearLayout tile(String label, String sub, Class<?> target,
                              android.view.View.OnClickListener custom) {
        LinearLayout c = UiKit.card(this);
        TextView t = UiKit.title(this, label, 16);
        c.addView(t);
        c.addView(UiKit.muted(this, sub));
        c.setOnClickListener(custom != null ? custom
                : v -> startActivity(new Intent(this, target)));
        return c;
    }

    /* ---------- devices ---------- */
    static boolean hasKeyboard() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && (d.getSources() & InputDevice.SOURCE_KEYBOARD) != 0) return true;
        }
        return false;
    }

    static boolean hasMouse() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_MOUSE) != 0 || (s & InputDevice.SOURCE_TOUCHPAD) != 0) return true;
        }
        return false;
    }

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void reloadOverlay() {
        if (!OverlayService.running) return;
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(OverlayService.ACTION_RELOAD);
        try { startService(i); } catch (Exception ignored) { }
    }

    private void startController() {
        if (!canOverlay()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "Floating panel permission dein", Toast.LENGTH_LONG).show();
            return;
        }
        if (!KeyAccessibilityService.isRunning()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "X Controller accessibility ON karein", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "Controller chalu", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }
}
