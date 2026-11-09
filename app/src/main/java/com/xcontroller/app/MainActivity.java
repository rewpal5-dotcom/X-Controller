package com.xcontroller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * X Controller — Main screen.
 * Home → My Games (profiles) → Editor → Start Controller (floating overlay).
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF0B0F17;
    private static final int CARD = 0xFF141B28;
    private static final int ACCENT = 0xFF22D3EE;
    private static final int TEXT = 0xFFE9EEF7;
    private static final int MUTED = 0xFF8A97AC;

    private FrameLayout root;
    private ProfileStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProfileStore(this);
        store.ensureDefault();

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root.getChildCount() > 0 && root.getChildAt(0).getTag() != null
                && "home".equals(root.getChildAt(0).getTag())) {
            showHome();
        }
    }

    /* ================= helpers ================= */

    private TextView title(String t, int size) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(TEXT);
        v.setTextSize(size);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private TextView muted(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(MUTED);
        v.setTextSize(12.5f);
        return v;
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(14.5f);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(28f);
        b.setBackground(g);
        b.setPadding(24, 14, 24, 14);
        return b;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(34f);
        g.setStroke(2, 0xFF22303F);
        l.setBackground(g);
        l.setPadding(38, 34, 38, 34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 26;
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout screen() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        l.setPadding(42, 42, 42, 42);
        return l;
    }

    private void setScreen(View v, String tag) {
        v.setTag(tag);
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /* ================= HOME ================= */

    private void showHome() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout box = screen();

        TextView logo = title("X CONTROLLER", 27);
        logo.setTextColor(ACCENT);
        box.addView(logo);
        box.addView(muted("Universal Keyboard & Mouse Gaming App"));

        // device status card
        LinearLayout st = card();
        st.addView(title("Connected devices", 15));
        boolean kb = hasKeyboard(), ms = hasMouse();
        TextView k = new TextView(this);
        k.setText((kb ? "⌨  Keyboard: connected" : "⌨  Keyboard: not connected (OTG/Bluetooth lagayein)"));
        k.setTextColor(kb ? 0xFF22C55E : MUTED);
        k.setTextSize(13.5f);
        k.setPadding(0, 18, 0, 6);
        TextView m = new TextView(this);
        m.setText((ms ? "🖱  Mouse: connected" : "🖱  Mouse: not connected (OTG/Bluetooth lagayein)"));
        m.setTextColor(ms ? 0xFF22C55E : MUTED);
        m.setTextSize(13.5f);
        m.setPadding(0, 0, 0, 4);
        st.addView(k);
        st.addView(m);
        box.addView(st);

        // permissions card
        LinearLayout perm = card();
        perm.addView(title("Setup", 15));
        boolean overlayOk = canOverlay();
        boolean a11yOk = KeyAccessibilityService.isRunning();

        TextView p1 = new TextView(this);
        p1.setText((overlayOk ? "✅" : "⚠️") + "  Floating panel permission");
        p1.setTextColor(overlayOk ? 0xFF22C55E : 0xFFF59E0B);
        p1.setTextSize(13.5f);
        p1.setPadding(0, 16, 0, 8);
        p1.setOnClickListener(v -> {
            if (!canOverlay()) openOverlayPermission();
        });
        perm.addView(p1);

        TextView p2 = new TextView(this);
        p2.setText((a11yOk ? "✅" : "⚠️") + "  Touch mapping service (Accessibility)");
        p2.setTextColor(a11yOk ? 0xFF22C55E : 0xFFF59E0B);
        p2.setTextSize(13.5f);
        p2.setPadding(0, 0, 0, 8);
        p2.setOnClickListener(v -> openAccessibilitySettings());
        perm.addView(p2);

        Button fix = button("Permissions theek karein", 0xFF1E2A3A, ACCENT);
        fix.setOnClickListener(v -> {
            if (!canOverlay()) openOverlayPermission();
            else if (!KeyAccessibilityService.isRunning()) openAccessibilitySettings();
            else Toast.makeText(this, "Sab theek hai ✅", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = 22;
        perm.addView(fix, flp);
        box.addView(perm);

        // main actions
        Button start = button("▶  Start Controller", ACCENT, 0xFF04121A);
        start.setOnClickListener(v -> startController());
        box.addView(start);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = 22;
        row.setLayoutParams(rlp);

        Button games = button("🎮  My Games", 0xFF1E2A3A, TEXT);
        games.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        games.setOnClickListener(v -> showGames());
        row.addView(games);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(20, 1));

        Button help = button("❓  Help", 0xFF1E2A3A, TEXT);
        help.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        help.setOnClickListener(v -> showHelp());
        row.addView(help);

        box.addView(row);

        ProfileStore.Profile active = store.active();
        TextView act = muted("Active profile: " + (active == null ? "—" : active.name)
                + " · " + (active == null ? 0 : active.bindings.size()) + " keys");
        act.setPadding(0, 26, 0, 0);
        box.addView(act);

        sv.addView(box);
        setScreen(sv, "home");
    }

    private boolean hasKeyboard() {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice d = android.view.InputDevice.getDevice(id);
            if (d == null) continue;
            int src = d.getSources();
            if ((src & android.view.InputDevice.SOURCE_KEYBOARD) != 0) return true;
        }
        return false;
    }

    private boolean hasMouse() {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice d = android.view.InputDevice.getDevice(id);
            if (d == null) continue;
            int src = d.getSources();
            if ((src & android.view.InputDevice.SOURCE_MOUSE) != 0
                    || (src & android.view.InputDevice.SOURCE_TOUCHPAD) != 0) return true;
        }
        return false;
    }

    private boolean canOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return Settings.canDrawOverlays(this);
        return true;
    }

    private void openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }
    }

    private void openAccessibilitySettings() {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(i);
        Toast.makeText(this, "X Controller dhoondh kar ON karein", Toast.LENGTH_LONG).show();
    }

    private void startController() {
        if (!canOverlay()) {
            openOverlayPermission();
            Toast.makeText(this, "Pehle floating panel permission dein", Toast.LENGTH_LONG).show();
            return;
        }
        if (!KeyAccessibilityService.isRunning()) {
            openAccessibilitySettings();
            Toast.makeText(this, "Pehle Accessibility service ON karein", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "Controller chalu — ab game kholein", Toast.LENGTH_SHORT).show();
        finish();
    }

    /* ================= GAMES / PROFILES ================= */

    private void showGames() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout box = screen();

        box.addView(title("My Games", 24));
        box.addView(muted("Har game ke liye alag control profile"));

        List<ProfileStore.Profile> list = store.all();
        ProfileStore.Profile active = store.active();

        for (final ProfileStore.Profile p : list) {
            LinearLayout c = card();
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            texts.addView(title(p.name, 16));
            texts.addView(muted(p.bindings.size() + " keys · sensitivity " + p.sensitivity + "%"));
            head.addView(texts);

            if (active != null && active.name.equalsIgnoreCase(p.name)) {
                TextView badge = new TextView(this);
                badge.setText("ACTIVE");
                badge.setTextColor(0xFF04121A);
                badge.setTextSize(10.5f);
                badge.setTypeface(null, android.graphics.Typeface.BOLD);
                GradientDrawable g = new GradientDrawable();
                g.setColor(ACCENT);
                g.setCornerRadius(20f);
                badge.setBackground(g);
                badge.setPadding(18, 8, 18, 8);
                head.addView(badge);
            }
            c.addView(head);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = 22;
            actions.setLayoutParams(alp);

            Button edit = button("✏  Edit", 0xFF1E2A3A, TEXT);
            edit.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            edit.setOnClickListener(v -> showEditor(p.name));
            actions.addView(edit);

            View g1 = new View(this); g1.setLayoutParams(new LinearLayout.LayoutParams(16, 1));
            actions.addView(g1);

            Button use = button("✓  Use", 0xFF13343F, ACCENT);
            use.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            use.setOnClickListener(v -> {
                store.setActive(p.name);
                Toast.makeText(this, p.name + " active", Toast.LENGTH_SHORT).show();
                showGames();
            });
            actions.addView(use);

            View g2 = new View(this); g2.setLayoutParams(new LinearLayout.LayoutParams(16, 1));
            actions.addView(g2);

            Button del = button("🗑", 0xFF3A1E22, 0xFFEF4444);
            del.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Profile delete?")
                    .setMessage(p.name + " hat jayega.")
                    .setPositiveButton("Delete", (d, w) -> { store.delete(p.name); showGames(); })
                    .setNegativeButton("Cancel", null)
                    .show());
            actions.addView(del);

            c.addView(actions);
            box.addView(c);
        }

        Button add = button("+  Naya profile", ACCENT, 0xFF04121A);
        add.setOnClickListener(v -> askProfileName());
        box.addView(add);

        Button back = button("‹  Back", 0xFF1E2A3A, TEXT);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = 20;
        back.setLayoutParams(blp);
        back.setOnClickListener(v -> showHome());
        box.addView(back);

        sv.addView(box);
        setScreen(sv, "games");
    }

    private void askProfileName() {
        EditText et = new EditText(this);
        et.setHint("Game ka naam (jaise Free Fire)");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Naya profile")
                .setView(et)
                .setPositiveButton("Banao", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "Profile " + (store.all().size() + 1);
                    ProfileStore.Profile p = new ProfileStore.Profile(name);
                    store.upsert(p);
                    store.setActive(name);
                    showEditor(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= EDITOR ================= */

    private void showEditor(final String profileName) {
        ProfileStore.Profile profile = null;
        for (ProfileStore.Profile p : store.all()) {
            if (p.name.equalsIgnoreCase(profileName)) { profile = p; break; }
        }
        if (profile == null) { showGames(); return; }
        final ProfileStore.Profile editing = profile;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(30, 30, 30, 12);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button back = button("‹", 0xFF1E2A3A, TEXT);
        back.setOnClickListener(v -> showGames());
        top.addView(back);

        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(24, 0, 0, 0);
        tt.addView(title(editing.name, 17));
        tt.addView(muted("Tap = naya button · drag = hilayein · long-press = delete"));
        top.addView(tt);
        wrap.addView(top);

        final EditorView canvas = new EditorView(this, editing);
        canvas.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wrap.addView(canvas);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(30, 16, 30, 30);

        Button add = button("+ Key", ACCENT, 0xFF04121A);
        add.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        add.setOnClickListener(v -> pickKey(editing, canvas));
        bottom.addView(add);

        View g = new View(this); g.setLayoutParams(new LinearLayout.LayoutParams(18, 1));
        bottom.addView(g);

        Button save = button("✓ Save", 0xFF13343F, ACCENT);
        save.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        save.setOnClickListener(v -> {
            store.upsert(editing);
            store.setActive(editing.name);
            Toast.makeText(this, "Profile save ho gaya", Toast.LENGTH_SHORT).show();
            showGames();
        });
        bottom.addView(save);

        wrap.addView(bottom);
        setScreen(wrap, "editor");
    }

    private void pickKey(final ProfileStore.Profile profile, final EditorView canvas) {
        final String[] keys = {
                "W", "A", "S", "D", "SPACE", "SHIFT", "CTRL", "R", "F", "E", "Q",
                "1", "2", "3", "4", "TAB", "ENTER", "ESC", "MOUSE_LEFT", "MOUSE_RIGHT"
        };
        new AlertDialog.Builder(this)
                .setTitle("Kaunsi key?")
                .setItems(keys, (d, which) -> {
                    profile.bindings.add(new ProfileStore.Binding(keys[which], 0.5f, 0.5f));
                    canvas.invalidate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* canvas: dots ko drag karo */
    static class EditorView extends View {
        private final ProfileStore.Profile profile;
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int dragging = -1;
        private long downTime = 0;
        private float downX, downY;

        EditorView(Context c, ProfileStore.Profile p) {
            super(c);
            profile = p;
            dot.setColor(0x5522D3EE);
            ring.setColor(0xFF22D3EE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(4f);
            txt.setColor(Color.WHITE);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(34f);
            grid.setColor(0x14FFFFFF);
            grid.setStrokeWidth(2f);
            setBackgroundColor(0xFF070B12);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            for (int i = 1; i < 10; i++) {
                c.drawLine(w * i / 10f, 0, w * i / 10f, h, grid);
                c.drawLine(0, h * i / 10f, w, h * i / 10f, grid);
            }
            float r = 52f;
            for (ProfileStore.Binding b : profile.bindings) {
                float x = b.x * w, y = b.y * h;
                c.drawCircle(x, y, r, dot);
                c.drawCircle(x, y, r, ring);
                c.drawText(b.key, x, y + 11f, txt);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int w = getWidth(), h = getHeight();
            float r = 70f;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downTime = System.currentTimeMillis();
                    downX = e.getX();
                    downY = e.getY();
                    dragging = -1;
                    for (int i = profile.bindings.size() - 1; i >= 0; i--) {
                        ProfileStore.Binding b = profile.bindings.get(i);
                        float dx = b.x * w - e.getX(), dy = b.y * h - e.getY();
                        if (Math.sqrt(dx * dx + dy * dy) < r) { dragging = i; break; }
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging >= 0) {
                        ProfileStore.Binding b = profile.bindings.get(dragging);
                        b.x = Math.max(0.03f, Math.min(0.97f, e.getX() / w));
                        b.y = Math.max(0.03f, Math.min(0.97f, e.getY() / h));
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging >= 0 && System.currentTimeMillis() - downTime > 550
                            && Math.abs(e.getX() - downX) < 20 && Math.abs(e.getY() - downY) < 20) {
                        profile.bindings.remove(dragging);   // long-press = delete
                        invalidate();
                    }
                    dragging = -1;
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /* ================= HELP ================= */

    private void showHelp() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout box = screen();

        box.addView(title("Kaise chalayein", 23));
        TextView t = new TextView(this);
        t.setTextColor(TEXT);
        t.setTextSize(14.5f);
        t.setLineSpacing(10f, 1f);
        t.setPadding(0, 24, 0, 0);
        t.setText("1. OTG ya Bluetooth se keyboard/mouse jodein\n\n"
                + "2. Home par dono permissions ✅ kar lein\n\n"
                + "3. My Games → apna game profile kholein → keys add karein\n"
                + "   (tap = naya key, drag = jagah badlein, long-press = delete)\n\n"
                + "4. Save dabayein, phir Start Controller\n\n"
                + "5. Ab game kholein — upar floating panel dikhega\n"
                + "   ✥ se hilayein, KEY ON/OFF se mapping chalu/band karein, ✕ se band\n\n"
                + "Zaroori baat: har game mein mapping kaam nahi karti.\n"
                + "Kuch games anti-cheat ki wajah se bahari control block karte hain.\n"
                + "Ye app sirf un games ke liye hai jahan Android aur game ki policy\n"
                + "iski ijazat deti hai.");
        box.addView(t);

        Button back = button("‹  Back", 0xFF1E2A3A, TEXT);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = 30;
        back.setLayoutParams(blp);
        back.setOnClickListener(v -> showHome());
        box.addView(back);

        sv.addView(box);
        setScreen(sv, "help");
    }
}
