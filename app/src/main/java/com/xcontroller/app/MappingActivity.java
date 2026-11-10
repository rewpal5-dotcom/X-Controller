package com.xcontroller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Custom Controls — keyboard/mouse key mapping editor. */
public class MappingActivity extends Activity {

    static final String[] KEYS = {
            "W", "A", "S", "D", "SPACE", "SHIFT", "CTRL", "ALT", "R", "F", "E", "Q",
            "1", "2", "3", "4", "TAB", "ENTER", "ESC",
            "MOUSE_LEFT", "MOUSE_RIGHT", "MOUSE_MIDDLE", "SCROLL_UP", "SCROLL_DOWN"
    };
    static final String[] ACTIONS = {
            "Move Forward", "Move Left", "Move Back", "Move Right", "Jump", "Sprint",
            "Reload", "Interact", "Use Item", "Special Ability", "Inventory", "Pause",
            "Fire", "Aim", "Weapon Change", "Aim Movement", "Custom"
    };

    private ProfileStore store;
    private ProfileStore.Profile profile;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new ProfileStore(this);
        String name = getIntent().getStringExtra("profile");
        profile = name == null ? store.active() : store.get(name);
        if (profile == null) { finish(); return; }
        build();
    }

    private void build() {
        LinearLayout box = UiKit.column(this);
        box.addView(UiKit.title(this, "Custom Controls", 22));
        box.addView(UiKit.muted(this, profile.name + " · key → game action"));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);

        LinearLayout row = UiKit.row(this);
        Button capture = UiKit.button(this, "⌨  Key dabayein (auto detect)", UiKit.PURPLE, 0xFFFFFFFF);
        capture.setOnClickListener(v -> captureKey());
        box.addView(capture, UiKit.matchWrap(6));

        Button add = UiKit.button(this, "+  Key list se chunein", UiKit.ACCENT, 0xFF04121A);
        add.setLayoutParams(UiKit.weight(1f));
        add.setOnClickListener(v -> addKey());
        row.addView(add);
        row.addView(UiKit.spacer(this, 14, 1));

        Button reset = UiKit.button(this, "↺ Reset", 0xFF1B2636, UiKit.TEXT);
        reset.setLayoutParams(UiKit.weight(1f));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset mapping?")
                .setMessage("Saare keys hat jayenge.")
                .setPositiveButton("Reset", (d, w) -> { profile.bindings.clear(); save(); })
                .setNegativeButton("Cancel", null).show());
        row.addView(reset);
        box.addView(row, UiKit.matchWrap(16));

        LinearLayout row2 = UiKit.row(this);
        Button export = UiKit.button(this, "⤴ Export", 0xFF1B2636, UiKit.TEXT);
        export.setLayoutParams(UiKit.weight(1f));
        export.setOnClickListener(v -> exportProfile());
        row2.addView(export);
        row2.addView(UiKit.spacer(this, 14, 1));
        Button imp = UiKit.button(this, "⤵ Import", 0xFF1B2636, UiKit.TEXT);
        imp.setLayoutParams(UiKit.weight(1f));
        imp.setOnClickListener(v -> importProfile());
        row2.addView(imp);
        box.addView(row2, UiKit.matchWrap(12));

        Button layout = UiKit.button(this, "📐  Layout editor (position, size)", 0xFF123542, UiKit.ACCENT);
        layout.setOnClickListener(v -> startActivity(new android.content.Intent(this, LayoutActivity.class)
                .putExtra("profile", profile.name)));
        box.addView(layout, UiKit.matchWrap(16));

        ScrollView sv = UiKit.scroll(this, box);
        setContentView(sv);
        render();
    }

    private void render() {
        list.removeAllViews();
        if (profile.bindings.isEmpty()) {
            list.addView(UiKit.muted(this, "Koi key nahi — '+ Key add' dabayein"));
            return;
        }
        for (int i = 0; i < profile.bindings.size(); i++) {
            final ProfileStore.Binding b = profile.bindings.get(i);
            LinearLayout c = UiKit.card(this);
            LinearLayout row = UiKit.row(this);
            TextView k = UiKit.title(this, b.key, 16);
            k.setTextColor(UiKit.ACCENT);
            k.setLayoutParams(UiKit.weight(1f));
            row.addView(k);
            TextView a = UiKit.body(this, (b.action == null || b.action.isEmpty()) ? "—" : b.action,
                    13.5f, UiKit.TEXT);
            a.setLayoutParams(UiKit.weight(1.4f));
            row.addView(a);
            c.addView(row);

            c.addView(UiKit.muted(this, "jagah: " + Math.round(b.x * 100) + "% , " + Math.round(b.y * 100) + "%"));

            LinearLayout acts = UiKit.row(this);
            acts.setPadding(0, 14, 0, 0);

            Button test = UiKit.button(this, "Test", 0xFF123542, UiKit.ACCENT);
            test.setLayoutParams(UiKit.weight(1f));
            test.setOnClickListener(v -> {
                KeyAccessibilityService svc = KeyAccessibilityService.get();
                if (svc == null) {
                    android.widget.Toast.makeText(this, "Accessibility ON nahi hai",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                int[] sc = screenSize();
                svc.tap(b.x * sc[0], b.y * sc[1]);
                android.widget.Toast.makeText(this, "Tap bheja: " + Math.round(b.x * 100) + "%, "
                        + Math.round(b.y * 100) + "%", android.widget.Toast.LENGTH_SHORT).show();
            });
            acts.addView(test);
            acts.addView(UiKit.spacer(this, 12, 1));

            Button ch = UiKit.button(this, "Change", 0xFF1B2636, UiKit.TEXT);
            ch.setLayoutParams(UiKit.weight(1f));
            ch.setOnClickListener(v -> changeKey(b));
            acts.addView(ch);
            acts.addView(UiKit.spacer(this, 12, 1));
            Button rm = UiKit.button(this, "Remove", 0xFF3A1E22, UiKit.RED);
            rm.setLayoutParams(UiKit.weight(1f));
            rm.setOnClickListener(v -> { profile.bindings.remove(b); save(); });
            acts.addView(rm);
            c.addView(acts);
            list.addView(c);
        }
    }

    /** Asli keyboard se key pakdo — jo dabayein wahi key save hogi (koi mismatch nahi). */
    private void captureKey() {
        final android.app.Dialog d = new android.app.Dialog(this);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText("Ab keyboard par koi key dabayein…\n(wahi key is button se jud jayegi)");
        tv.setTextSize(18f);
        tv.setPadding(60, 80, 60, 80);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setFocusableInTouchMode(true);
        tv.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                String name = keyNameOf(code);
                if (name != null) {
                    addBinding(name);
                    d.dismiss();
                    return true;
                }
            }
            return false;
        });
        d.setContentView(tv);
        d.show();
        tv.requestFocus();
    }

    /** Overlay ke keyName() jaisa hi mapping — dono jagah same naam. */
    private String keyNameOf(int code) {
        if (code >= android.view.KeyEvent.KEYCODE_A && code <= android.view.KeyEvent.KEYCODE_Z)
            return String.valueOf((char) ('A' + (code - android.view.KeyEvent.KEYCODE_A)));
        if (code >= android.view.KeyEvent.KEYCODE_0 && code <= android.view.KeyEvent.KEYCODE_9)
            return String.valueOf((char) ('0' + (code - android.view.KeyEvent.KEYCODE_0)));
        switch (code) {
            case android.view.KeyEvent.KEYCODE_SPACE: return "SPACE";
            case android.view.KeyEvent.KEYCODE_SHIFT_LEFT:
            case android.view.KeyEvent.KEYCODE_SHIFT_RIGHT: return "SHIFT";
            case android.view.KeyEvent.KEYCODE_CTRL_LEFT:
            case android.view.KeyEvent.KEYCODE_CTRL_RIGHT: return "CTRL";
            case android.view.KeyEvent.KEYCODE_ALT_LEFT:
            case android.view.KeyEvent.KEYCODE_ALT_RIGHT: return "ALT";
            case android.view.KeyEvent.KEYCODE_TAB: return "TAB";
            case android.view.KeyEvent.KEYCODE_ENTER: return "ENTER";
            case android.view.KeyEvent.KEYCODE_ESCAPE: return "ESC";
            default: return null;
        }
    }

    /** Ek hi key dobara add na ho — purani update ho jaaye. */
    private void addBinding(String key) {
        ProfileStore.Binding existing = profile.find(key);
        if (existing != null) {
            android.widget.Toast.makeText(this, key + " pehle se hai — usi ko update kiya",
                    android.widget.Toast.LENGTH_SHORT).show();
            pickAction(existing);
            return;
        }
        ProfileStore.Binding b = new ProfileStore.Binding(key, 0.5f, 0.5f, "");
        pickAction(b);
    }

    private void addKey() {
        new AlertDialog.Builder(this)
                .setTitle("Kaunsi key?")
                .setItems(KEYS, (d, which) -> pickAction(new ProfileStore.Binding(KEYS[which], 0.5f, 0.5f, "")))
                .setNegativeButton("Cancel", null).show();
    }

    private void changeKey(final ProfileStore.Binding b) {
        new AlertDialog.Builder(this)
                .setTitle("Nayi key chunein")
                .setItems(KEYS, (d, which) -> { b.key = KEYS[which]; save(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void pickAction(final ProfileStore.Binding b) {
        new AlertDialog.Builder(this)
                .setTitle("Game action?")
                .setItems(ACTIONS, (d, which) -> {
                    String act = ACTIONS[which];
                    if (act.equals("Custom")) {
                        EditText et = new EditText(this);
                        et.setHint("Action ka naam");
                        new AlertDialog.Builder(this).setTitle("Custom action").setView(et)
                                .setPositiveButton("OK", (dd, ww) -> {
                                    b.action = et.getText().toString().trim();
                                    profile.bindings.add(b);
                                    save();
                                }).setNegativeButton("Cancel", null).show();
                    } else {
                        b.action = act;
                        profile.bindings.add(b);
                        save();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private int[] screenSize() {
        DisplayMetrics dm = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect r = getWindowManager().getCurrentWindowMetrics().getBounds();
            return new int[]{r.width(), r.height()};
        }
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private void save() {
        store.upsert(profile);
        store.setActive(profile.name);
        render();
        Toast.makeText(this, "Mapping save ho gayi", Toast.LENGTH_SHORT).show();
    }

    private void exportProfile() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", profile.name);
            o.put("sens", profile.sensitivity);
            JSONArray arr = new JSONArray();
            for (ProfileStore.Binding b : profile.bindings) {
                JSONObject bo = new JSONObject();
                bo.put("key", b.key); bo.put("action", b.action);
                bo.put("x", b.x); bo.put("y", b.y); bo.put("size", b.size);
                arr.put(bo);
            }
            o.put("bindings", arr);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("XController profile", o.toString()));
            Toast.makeText(this, "Profile clipboard mein copy ho gaya", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export nahi hua", Toast.LENGTH_SHORT).show();
        }
    }

    private void importProfile() {
        EditText et = new EditText(this);
        et.setHint("Profile JSON paste karein");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(4);
        new AlertDialog.Builder(this)
                .setTitle("Import profile")
                .setView(et)
                .setPositiveButton("Import", (d, w) -> {
                    try {
                        JSONObject o = new JSONObject(et.getText().toString().trim());
                        profile.bindings.clear();
                        profile.sensitivity = o.optInt("sens", 50);
                        JSONArray arr = o.getJSONArray("bindings");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject b = arr.getJSONObject(i);
                            ProfileStore.Binding bd = new ProfileStore.Binding(
                                    b.optString("key", "A"),
                                    (float) b.optDouble("x", 0.5),
                                    (float) b.optDouble("y", 0.5),
                                    b.optString("action", ""));
                            bd.size = (float) b.optDouble("size", 1.0);
                            profile.bindings.add(bd);
                        }
                        save();
                    } catch (Exception e) {
                        Toast.makeText(this, "JSON galat hai", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
