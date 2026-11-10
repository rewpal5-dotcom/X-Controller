package com.xcontroller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings — sensitivity, invert Y, theme, default profile, reset. */
public class SettingsActivity extends Activity {

    private ProfileStore store;
    private ProfileStore.Profile profile;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new ProfileStore(this);
        profile = store.active();
        if (profile == null) { finish(); return; }
        build();
    }

    private void build() {
        LinearLayout box = UiKit.column(this);
        box.addView(UiKit.title(this, "Settings", 22));
        box.addView(UiKit.muted(this, profile.name + " ke liye"));

        LinearLayout c = UiKit.card(this);
        c.addView(UiKit.title(this, "Mouse & Aim", 15));
        c.addView(slider("Mouse sensitivity", profile.sensitivity, v -> { profile.sensitivity = v; save(); }));
        c.addView(slider("Aim sensitivity", profile.aimSensitivity, v -> { profile.aimSensitivity = v; save(); }));
        c.addView(slider("Pointer speed", profile.pointerSpeed, v -> { profile.pointerSpeed = v; save(); }));

        Switch inv = new Switch(this);
        inv.setText("Invert Y axis");
        inv.setTextColor(UiKit.TEXT);
        inv.setChecked(profile.invertY);
        inv.setOnCheckedChangeListener((buttonView, isChecked) -> { profile.invertY = isChecked; save(); });
        c.addView(inv, UiKit.matchWrap(10));
        box.addView(c);

        LinearLayout cJoy = UiKit.card(this);
        cJoy.addView(UiKit.title(this, "Joystick (gol circle)", 15));
        Switch joy = new Switch(this);
        joy.setText("Virtual joystick dikhayein");
        joy.setTextColor(UiKit.TEXT);
        joy.setChecked(profile.joystick);
        joy.setOnCheckedChangeListener((v, on) -> { profile.joystick = on; save(); });
        cJoy.addView(joy, UiKit.matchWrap(8));
        cJoy.addView(UiKit.muted(this,
                "Game ke upar gol joystick — ungli/ghuma kar chalayein.\n"
              + "Overlay ke EDIT mode mein ise jahan chahein le jaayein."));
        cJoy.addView(slider("Joystick size", Math.round(profile.joyR * 400), v -> {
            profile.joyR = Math.max(0.06f, Math.min(0.20f, v / 400f)); save();
        }));
        box.addView(cJoy);

        LinearLayout cAim = UiKit.card(this);
        cAim.addView(UiKit.title(this, "Mouse aim", 15));
        Switch aim = new Switch(this);
        aim.setText("Mouse hilane par camera ghumayein");
        aim.setTextColor(UiKit.TEXT);
        aim.setChecked(profile.aimEnabled);
        aim.setOnCheckedChangeListener((v, on) -> { profile.aimEnabled = on; save(); });
        cAim.addView(aim, UiKit.matchWrap(8));
        cAim.addView(slider("Aim sensitivity", profile.aimSensitivity, v -> {
            profile.aimSensitivity = v; save();
        }));
        cAim.addView(UiKit.muted(this,
                "Mouse move → swipe gesture. Har game mein poori tarah kaam nahi karta;\n"
              + "jo game native mouse support dete hain wahan behtar chalta hai."));
        box.addView(cAim);

        LinearLayout c2 = UiKit.card(this);
        c2.addView(UiKit.title(this, "Button look", 15));
        c2.addView(slider("Button size", Math.round(profile.buttonSize * 50), v -> {
            profile.buttonSize = Math.max(0.6f, Math.min(1.8f, v / 50f)); save();
        }));
        c2.addView(slider("Button opacity", Math.round(profile.buttonOpacity * 100), v -> {
            profile.buttonOpacity = Math.max(0.3f, Math.min(1f, v / 100f)); save();
        }));
        box.addView(c2);

        LinearLayout c3 = UiKit.card(this);
        c3.addView(UiKit.title(this, "App", 15));

        Switch auto = new Switch(this);
        auto.setText("Auto start controller (experimental)");
        auto.setTextColor(UiKit.TEXT);
        auto.setChecked(store.autoStart());
        auto.setOnCheckedChangeListener((v, checked) -> store.setAutoStart(checked));
        c3.addView(auto, UiKit.matchWrap(8));

        Switch dbg = new Switch(this);
        dbg.setText("Input debugging");
        dbg.setTextColor(UiKit.TEXT);
        dbg.setChecked(store.inputDebug());
        dbg.setOnCheckedChangeListener((v, checked) -> store.setInputDebug(checked));
        c3.addView(dbg, UiKit.matchWrap(6));

        Button def = UiKit.button(this, "Default profile: " + profile.name, 0xFF1B2636, UiKit.TEXT);
        def.setOnClickListener(v -> {
            store.setDefaultProfile(profile.name);
            Toast.makeText(this, "Default profile set", Toast.LENGTH_SHORT).show();
        });
        c3.addView(def, UiKit.matchWrap(14));

        Button reset = UiKit.button(this, "Reset app (sab data mit jayega)", 0xFF3A1E22, UiKit.RED);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset app?")
                .setMessage("Saare profiles aur settings delete ho jayenge.")
                .setPositiveButton("Reset", (d, w) -> {
                    store.resetAll();
                    store.ensureDefault();
                    Toast.makeText(this, "Reset ho gaya", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null).show());
        c3.addView(reset, UiKit.matchWrap(16));
        box.addView(c3);

        ScrollView sv = UiKit.scroll(this, box);
        setContentView(sv);
    }

    interface OnValue { void onValue(int v); }

    private LinearLayout slider(String label, int value, final OnValue cb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 14, 0, 4);
        TextView t = UiKit.body(this, label + ": " + value, 13.5f, UiKit.TEXT);
        wrap.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(Math.max(1, Math.min(100, value)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int val = Math.max(1, p);
                t.setText(label + ": " + val);
                cb.onValue(val);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        wrap.addView(sb);
        return wrap;
    }

    private void save() { store.upsert(profile); }
}
