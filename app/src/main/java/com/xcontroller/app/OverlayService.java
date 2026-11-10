package com.xcontroller.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * OverlayService — floating controller (v3).
 *
 *  • Chhota floating button (bubble) — screen par kahin bhi rakh sakte hain
 *  • Bubble dabao → EDIT mode: saare control buttons dikhte hain, jahan chahein
 *    drag karke rakhein, size badlein, key badlein, delete karein, naya add karein
 *  • Done dabao → PLAY mode: buttons wahi rehte hain, keyboard se mapping chalti hai
 */
public class OverlayService extends Service {

    public static boolean running = false;
    private static OverlayService instance;

    private WindowManager wm;
    private PanelView panel;
    private BubbleView bubble;
    private JoystickView joystick;
    private AimPadView aimPad;
    private FireButtonView fireBtn;
    private WindowManager.LayoutParams fireParams;
    private LinearLayout menu;
    private WindowManager.LayoutParams panelParams, bubbleParams, joyParams, aimParams, menuParams;
    private boolean masterOn = false;   // jab tak user ON na kare, phone normal chalta rahe

    private ProfileStore store;
    private ProfileStore.Profile profile;
    private boolean editMode = false;
    private boolean keyMode = true;

    public static OverlayService get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        store = new ProfileStore(this);
        KeyAccessibilityService.handler = name -> handleKey(name);
        startForegroundNotice();
        showOverlay();
    }

    private void startForegroundNotice() {
        String channelId = "xcontroller";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(channelId, "X Controller",
                        NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
        }
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        b.setContentTitle("X Controller chal raha hai")
                .setContentText("Floating button se controls set karein")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, b.build(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, b.build());
        }
    }

    /* ================= setup ================= */

    private void showOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        profile = store.active();
        if (profile == null) { stopSelf(); return; }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;

        /* ---- full screen controls layer ---- */
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Har control apni chhoti window hai — poore screen par koi touchable layer nahi,
        // isliye phone/game ki touch hamesha kaam karti hai.
        buildControls(type);

        /* ---- floating bubble ---- */
        bubble = new BubbleView(this);
        bubble.keysOn = profile.keyCapture;
        bubble.callback = new BubbleView.Callback() {
            public void onTap() { toggleMenu(); }
            public void onLongPress() { setEditMode(!editMode); }
        };
        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 16;
        bubbleParams.y = Math.max(120, h / 3);

        try { wm.addView(bubble, bubbleParams); } catch (Exception ignored) { }

        buildMenu(type);
        applyMaster(masterOn);

        if (profile.keyCapture) setKeyCapture(true);

        /* ---- joystick (gol circle wala movement control) ---- */
        if (profile.joystick) {
            joystick = new JoystickView(this, profile, w, h);
            joystick.callback = new JoystickView.Callback() {
                public void onMove(float dx, float dy) { moveBy(dx, dy); }
                public void onRelease() { stopMoving(); }
                public void onStart() { joyActive = false; }
                public void onPosition(float nx, float ny) {
                    profile.joyX = nx; profile.joyY = ny; store.upsert(profile);
                }
            };
            joyParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            joyParams.gravity = Gravity.TOP | Gravity.START;
            joyParams.x = (int) (profile.joyX * w);
            joyParams.y = (int) (profile.joyY * h);
            try { wm.addView(joystick, joyParams); } catch (Exception ignored) { }
            joystick.setVisibility(View.GONE);
        }

        /* ---- FIRE button (mouse firing) ---- */
        if (profile.fireBtn) {
            fireBtn = new FireButtonView(this, profile);
            fireBtn.callback = new FireButtonView.Callback() {
                public void onFireDown() {
                    KeyAccessibilityService svc = KeyAccessibilityService.get();
                    if (svc == null) return;
                    int[] sc = screen();
                    svc.tap(profile.fireX * sc[0], profile.fireY * sc[1]);
                }
                public void onMoved(float nx, float ny) {
                    profile.fireX = nx; profile.fireY = ny; store.upsert(profile);
                }
            };
            fireParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            fireParams.gravity = Gravity.TOP | Gravity.START;
            fireParams.x = (int) (profile.fireX * w);
            fireParams.y = (int) (profile.fireY * h);
            try { wm.addView(fireBtn, fireParams); } catch (Exception ignored) { }
            fireBtn.setVisibility(View.GONE);
        }

        /* ---- mouse aim pad (mouse hilane par camera ghumana) ---- */
        if (profile.aimEnabled) {   // default OFF — full-screen touch layer games ko rok deta hai
            aimPad = new AimPadView(this, profile, w, h);
            aimPad.callback = new AimPadView.Callback() {
                public void onMove(float dx, float dy) { aimBy(dx, dy); }
                public void onFire() { handleKey("MOUSE_LEFT"); }
            };
            aimParams = new WindowManager.LayoutParams(
                    profile.aimZone.equals("full") ? WindowManager.LayoutParams.MATCH_PARENT : (int) (w * 0.38f),
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            aimParams.gravity = Gravity.TOP | Gravity.END;
            try { wm.addView(aimPad, aimParams); } catch (Exception ignored) { }
            aimPad.setVisibility(View.GONE);
        }
    }

    /* ---- joystick movement: game ke joystick par stroke bhejo ---- */
    private boolean joyActive = false;

    private void moveBy(float dx, float dy) {
        if (profile == null) return;
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) return;
        int[] sc = screen();
        float cx = profile.joyX * sc[0];
        float cy = profile.joyY * sc[1];
        float r = profile.joyR * sc[0];
        float tx = cx + dx * r;
        float ty = cy + dy * r;
        if (!joyActive) {
            joyActive = true;
            svc.joyStart(cx, cy);
        } else {
            svc.joyMove(tx, ty);
        }
    }

    private void stopMoving() {
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) { joyActive = false; return; }
        if (joyActive) {
            svc.joyEnd();
            joyActive = false;
        }
    }

    /* ---- mouse aim: mouse ka movement swipe gesture mein badlo ---- */
    private void aimBy(float dx, float dy) {
        if (profile == null) return;
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) return;
        int[] sc = screen();
        float sens = Math.max(0.4f, profile.aimSensitivity / 50f);
        float cx = sc[0] * 0.72f;
        float cy = sc[1] * 0.45f;
        float sx = cx - dx * 2.2f * sens;
        float sy = cy - dy * 2.2f * sens;
        sx = Math.max(sc[0] * 0.55f, Math.min(sc[0] - 8, sx));
        sy = Math.max(8, Math.min(sc[1] - 8, sy));
        svc.swipe(cx, cy, sx, sy, 55);
    }


    /* ===================== per-control windows ===================== */
    private final java.util.List<ControlView> controlViews = new java.util.ArrayList<>();

    private void buildControls(int type) {
        clearControls();
        if (profile == null) return;
        for (ProfileStore.Binding b : profile.bindings) {
            ControlView v = new ControlView(this, profile, b);
            v.callback = new ControlView.Callback() {
                public void onTap(ProfileStore.Binding bb) { fireAt(bb); }
                public void onMoved(ProfileStore.Binding bb) { store.upsert(profile); }
                public void onLongPress(ProfileStore.Binding bb) { showKeyMenu(bb); }
            };
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            int[] sc = screen();
            lp.x = (int) (b.x * sc[0]);
            lp.y = (int) (b.y * sc[1]);
            v.params = lp;
            try { wm.addView(v, lp); } catch (Exception ignored) { }
            v.setVisibility(masterOn ? View.VISIBLE : View.GONE);
            controlViews.add(v);
        }
    }

    private void clearControls() {
        for (ControlView v : controlViews) {
            try { wm.removeView(v); } catch (Exception ignored) { }
        }
        controlViews.clear();
    }

    private void fireAt(ProfileStore.Binding b) {
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null || profile == null) return;
        int[] sc = screen();
        svc.tap(b.x * sc[0] + 4, b.y * sc[1] + 4);
    }

    /* ---- key chooser (chhoti window) ---- */
    private LinearLayout keyMenu;

    private void showKeyMenu(final ProfileStore.Binding b) {
        hideKeyMenu();
        keyMenu = new LinearLayout(this);
        keyMenu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF00B0F17); g.setCornerRadius(26f); g.setStroke(3, 0xFFA855F7);
        keyMenu.setBackground(g);
        keyMenu.setPadding(20, 18, 20, 18);

        TextView head = new TextView(this);
        head.setText("Key chunein (" + b.key + ")");
        head.setTextColor(0xFFA855F7);
        head.setTextSize(13f);
        head.setPadding(0, 0, 0, 10);
        keyMenu.addView(head);

        String[] keys = {"A","B","C","D","E","F","G","H","I","J","K","L","M",
                         "N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
                         "SPACE","SHIFT","CTRL","TAB","MOUSE_LEFT","MOUSE_RIGHT","SCROLL_UP","SCROLL_DOWN"};
        LinearLayout row = null;
        for (int i = 0; i < keys.length; i++) {
            if (i % 6 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                keyMenu.addView(row);
            }
            final String k = keys[i];
            TextView tv = new TextView(this);
            tv.setText(k.length() > 3 ? k.substring(0, 3) : k);
            tv.setTextColor(0xFFEAF2FF);
            tv.setTextSize(12f);
            tv.setPadding(12, 14, 12, 14);
            tv.setOnClickListener(x -> {
                b.key = k;
                store.upsert(profile);
                hideKeyMenu();
                buildControls(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE);
            });
            row.addView(tv);
        }

        TextView del = new TextView(this);
        del.setText("🗑  Ye button delete karein");
        del.setTextColor(0xFFEF4444);
        del.setTextSize(13f);
        del.setPadding(0, 16, 0, 4);
        del.setOnClickListener(x -> {
            profile.bindings.remove(b);
            store.upsert(profile);
            hideKeyMenu();
            buildControls(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        });
        keyMenu.addView(del);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (260 * getResources().getDisplayMetrics().density),
                WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(keyMenu, lp); } catch (Exception ignored) { }
    }

    private void hideKeyMenu() {
        if (keyMenu != null) {
            try { wm.removeView(keyMenu); } catch (Exception ignored) { }
            keyMenu = null;
        }
    }

    /* ===================== floating menu ===================== */
    private void buildMenu(int type) {
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF00B0F17);
        g.setCornerRadius(30f);
        g.setStroke(3, 0xFF22D3EE);
        menu.setBackground(g);
        menu.setPadding(26, 22, 26, 22);

        menu.addView(menuTitle("X CONTROLLER"));
        menu.addView(menuBtn("master", masterOn ? "⏻  MASTER: ON" : "⏻  MASTER: OFF", true));
        menu.addView(menuBtn("keyboard", "⌨  Keyboard: " + onOff(profile.keyCapture), false));
        menu.addView(menuBtn("mouse", "🖱  Mouse: " + onOff(profile.aimEnabled), false));
        menu.addView(menuBtn("joystick", "🕹  Joystick: " + onOff(profile.joystick), false));
        menu.addView(menuBtn("fire", "🔥  Fire button: " + onOff(profile.fireBtn), false));
        menu.addView(menuBtn("add", "＋  Naya button add karein", false));
        menu.addView(menuBtn("edit", "✎  Poora editor kholein", false));
        menu.addView(menuBtn("close", "✕  Controller band karein", false));

        menuParams = new WindowManager.LayoutParams(
                (int) (210 * getResources().getDisplayMetrics().density),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.TOP | Gravity.START;
        menuParams.x = 90;
        menuParams.y = 200;
        menu.setVisibility(View.GONE);
        try { wm.addView(menu, menuParams); } catch (Exception ignored) { }
    }

    private String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private TextView menuTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFF22D3EE);
        v.setTextSize(13f);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, 0, 0, 14);
        return v;
    }

    private TextView menuBtn(String id, String label, boolean highlight) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(highlight ? 0xFF22C55E : 0xFFEAF2FF);
        v.setTextSize(15f);
        v.setPadding(6, 18, 6, 18);
        v.setTag(id);
        v.setOnClickListener(x -> {
            String tag = String.valueOf(v.getTag());
            if (tag.equals("master")) { toggleMaster(); }
            if (!tag.equals("edit")) hideMenu();
            else if (tag.equals("keyboard")) {
                profile.keyCapture = !profile.keyCapture; store.upsert(profile);
                setKeyCapture(profile.keyCapture); refreshMenu();
            } else if (tag.equals("mouse")) {
                profile.aimEnabled = !profile.aimEnabled; store.upsert(profile);
                reload(); refreshMenu();
            } else if (tag.equals("fire")) {
                profile.fireBtn = !profile.fireBtn; store.upsert(profile);
                reload(); refreshMenu();
            } else if (tag.equals("joystick")) {
                profile.joystick = !profile.joystick; store.upsert(profile);
                reload(); refreshMenu();
            } else if (tag.equals("add")) {
                ProfileStore.Binding nb = new ProfileStore.Binding("NEW", 0.5f, 0.5f, "");
                profile.bindings.add(nb);
                store.upsert(profile);
                hideMenu();
                buildControls(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE);
                showKeyMenu(nb);
            } else if (tag.equals("edit")) {
                hideMenu(); setEditMode(true);
            } else if (tag.equals("close")) {
                stopSelf();
            }
        });
        return v;
    }

    private void refreshMenu() {
        if (menu == null) return;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View c = menu.getChildAt(i);
            if (!(c instanceof TextView) || c.getTag() == null) continue;
            String tag = String.valueOf(c.getTag());
            TextView tv = (TextView) c;
            if (tag.equals("master")) {
                tv.setText(masterOn ? "⏻  MASTER: ON" : "⏻  MASTER: OFF");
                tv.setTextColor(masterOn ? 0xFF22C55E : 0xFFEF4444);
            } else if (tag.equals("keyboard")) tv.setText("⌨  Keyboard: " + onOff(profile.keyCapture));
            else if (tag.equals("mouse")) tv.setText("🖱  Mouse: " + onOff(profile.aimEnabled));
            else if (tag.equals("joystick")) tv.setText("🕹  Joystick: " + onOff(profile.joystick));
            else if (tag.equals("fire")) tv.setText("🔥  Fire button: " + onOff(profile.fireBtn));
        }
    }

    private void toggleMenu() {
        if (menu == null) return;
        if (menu.getVisibility() == View.VISIBLE) hideMenu();
        else {
            refreshMenu();
            menu.setVisibility(View.VISIBLE);
        }
    }

    private void hideMenu() {
        if (menu != null) menu.setVisibility(View.GONE);
    }

    /* ===================== master switch ===================== */
    private void toggleMaster() {
        masterOn = !masterOn;
        applyMaster(masterOn);
        refreshMenu();
    }

    private void applyMaster(boolean on) {
        masterOn = on;
        if (panel != null) panel.setVisibility(on ? View.VISIBLE : View.GONE);
        for (ControlView v : controlViews) v.setVisibility(on ? View.VISIBLE : View.GONE);
        if (joystick != null) joystick.setVisibility(on ? View.VISIBLE : View.GONE);
        if (fireBtn != null) fireBtn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (aimPad != null) aimPad.setVisibility(on ? View.VISIBLE : View.GONE);
        if (bubble != null) { bubble.masterOn = on; bubble.invalidate(); }
        if (!on) {
            setKeyCapture(false);
        } else {
            setKeyCapture(profile.keyCapture);
        }
    }

    /* ---- keyboard capture window (sirf jab user chahe) ---- */
    private KeySinkView keySink;
    private WindowManager.LayoutParams keyParams;

    private void setKeyCapture(boolean on) {
        profile.keyCapture = on;
        store.upsert(profile);
        // Ab koi focusable window nahi — keys AccessibilityService se aati hain.
        if (bubble != null) { bubble.keysOn = on; bubble.invalidate(); }
        return;
    }


    private void setEditMode(boolean on) {
        editMode = on;
        panel.editMode = on;
        panel.invalidate();
        if (panelParams != null && wm != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (on) {
                panelParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                panelParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            } else {
                panelParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                panelParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
            try { wm.updateViewLayout(panel, panelParams); } catch (Exception ignored) { }
        }
        if (!on) {
            store.upsert(profile);
        }
    }

    /** Asli screen size (rotation ke baad bhi sahi). */
    private int[] screen() {
        DisplayMetrics dm = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics wmtr = wm.getCurrentWindowMetrics();
            android.graphics.Rect b = wmtr.getBounds();
            return new int[]{b.width(), b.height()};
        } else {
            wm.getDefaultDisplay().getRealMetrics(dm);
            return new int[]{dm.widthPixels, dm.heightPixels};
        }
    }

    private boolean dispatching = false;

    private void handleKey(String keyName) {
        if (editMode || !keyMode || profile == null) return;
        if (dispatching) return;          // recursion guard (aim pad → fire → aim pad)
        dispatching = true;
        try {
            doHandleKey(keyName);
        } finally {
            dispatching = false;
        }
    }

    private void doHandleKey(String keyName) {
        ProfileStore.Binding b = profile.find(keyName);
        if (b == null) return;
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) return;
        int[] sc = screen();
        float px = b.x * sc[0], py = b.y * sc[1];
        Log.d("XController", "key " + keyName + " -> tap(" + (int) px + "," + (int) py + ") screen " + sc[0] + "x" + sc[1]);
        svc.tap(px, py);
    }

    public static final String ACTION_RELOAD = "com.xcontroller.app.RELOAD";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getBooleanExtra("stop", false)) { stopSelf(); return START_NOT_STICKY; }
            if (ACTION_RELOAD.equals(intent.getAction())) reload();
        }
        return START_STICKY;
    }

    /** Settings badalne par overlay ko naye profile ke saath dobara banao. */
    public void reload() {
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) { }
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) { }
        try { if (joystick != null) wm.removeView(joystick); } catch (Exception ignored) { }
        try { if (aimPad != null) wm.removeView(aimPad); } catch (Exception ignored) { }
        try { if (fireBtn != null) wm.removeView(fireBtn); } catch (Exception ignored) { }
        try { if (menu != null) wm.removeView(menu); } catch (Exception ignored) { }
        try { if (keySink != null) wm.removeView(keySink); } catch (Exception ignored) { }
        try { if (fireBtn != null) wm.removeView(fireBtn); } catch (Exception ignored) { }
        fireBtn = null;
        clearControls();
        hideKeyMenu();
        panel = null; bubble = null; joystick = null; aimPad = null; keySink = null;
        editMode = false;
        profile = store.active();
        if (profile == null) { stopSelf(); return; }
        showOverlay();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("XController", "configuration changed -> reload overlay");
        reload();
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        KeyAccessibilityService.handler = null;
        clearControls();
        hideKeyMenu();
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) { }
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) { }
        try { if (joystick != null) wm.removeView(joystick); } catch (Exception ignored) { }
        try { if (aimPad != null) wm.removeView(aimPad); } catch (Exception ignored) { }
        try { if (fireBtn != null) wm.removeView(fireBtn); } catch (Exception ignored) { }
        try { if (menu != null) wm.removeView(menu); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /* ===================== floating bubble ===================== */
    static class BubbleView extends View {
        interface Callback { void onTap(); void onLongPress(); }
        Callback callback;
        boolean keysOn = false;
        boolean masterOn = false;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private float downX, downY;
        private long downAt;
        private boolean moved;

        BubbleView(Context c) {
            super(c);
            size = (int) (58 * c.getResources().getDisplayMetrics().density);
            bg.setColor(0xE60B0F17);
            ring.setColor(0xFF22D3EE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(5f);
            txt.setColor(0xFF22D3EE);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(size * 0.42f);
            txt.setFakeBoldText(true);
        }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(size, size); }

        @Override
        protected void onDraw(Canvas c) {
            float r = size / 2f;
            c.drawCircle(r, r, r - 4, bg);
            c.drawCircle(r, r, r - 4, ring);
            int col = masterOn ? (keysOn ? 0xFF22C55E : 0xFF22D3EE) : 0xFF64748B;
            txt.setColor(col);
            ring.setColor(col);
            c.drawCircle(r, r, r - 4, ring);
            c.drawText("X", r, r + size * 0.15f, txt);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    downAt = System.currentTimeMillis();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX() - downX) > 12 || Math.abs(e.getRawY() - downY) > 12) {
                        moved = true;
                        lp.x = (int) (e.getRawX() - size / 2f);
                        lp.y = (int) (e.getRawY() - size / 2f);
                        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        long dur = System.currentTimeMillis() - downAt;
                        if (dur > 550) { if (callback != null) callback.onLongPress(); }
                        else { if (callback != null) callback.onTap(); }
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /* ===================== controls layer ===================== */
    static class PanelView extends FrameLayout {
        interface Callback {
            void onKey(String key);
            void onEditToggle();
            void onClose();
            void onKeyToggle();
            void onChanged();
        }

        Callback callback;
        boolean editMode = false;
        boolean keyMode = true;

        private final ProfileStore.Profile profile;
        private final List<ProfileStore.Binding> bindings;
        private final int screenW, screenH;

        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private int selected = -1;
        private int dragIndex = -1;
        private float dragDX, dragDY;
        private long downAt;
        private float downX, downY;
        private boolean chooserOpen = false;
        private ProfileStore.Binding pendingBinding = null;

        private static final String[] KEY_CHOICES = {
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
                "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
                "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4",
                "5", "6", "7", "8", "9", "0", "SPACE", "SHIFT", "CTRL", "ALT",
                "TAB", "ENTER", "ESC", "MOUSE_LEFT", "MOUSE_RIGHT",
                "MOUSE_MIDDLE", "SCROLL_UP", "SCROLL_DOWN"
        };
        private static final int COLS = 5;

        PanelView(Context c, ProfileStore.Profile p, int w, int h) {
            super(c);
            profile = p;
            bindings = p.bindings;
            screenW = w; screenH = h;
            setWillNotDraw(false);
            setFocusable(true);
            setFocusableInTouchMode(true);

            dot.setColor(0x5522D3EE);
            ring.setColor(0xCC22D3EE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(4f);
            label.setColor(0xFFFFFFFF);
            label.setTextAlign(Paint.Align.CENTER);
            bar.setColor(0xF00B0F17);
            accent.setColor(0xFF22D3EE);
        }

        /** Saare controls ko cover karne wala chhota box: {x, y, w, h} */
        int[] contentBox() {
            int w = getWidth() > 0 ? getWidth() : screenW;
            int h = getHeight() > 0 ? getHeight() : screenH;
            if (bindings.isEmpty()) return new int[]{0, 0, 4, 4};
            float minX = 1f, minY = 1f, maxX = 0f, maxY = 0f;
            for (ProfileStore.Binding b : bindings) {
                float r = radius(b) / Math.max(1, w);
                float ry = radius(b) / Math.max(1, h);
                minX = Math.min(minX, b.x - r); maxX = Math.max(maxX, b.x + r);
                minY = Math.min(minY, b.y - ry); maxY = Math.max(maxY, b.y + ry);
            }
            int x = Math.max(0, (int) (minX * w) - 10);
            int y = Math.max(0, (int) (minY * h) - 10);
            int cw = Math.min(w - x, (int) ((maxX - minX) * w) + 20);
            int ch = Math.min(h - y, (int) ((maxY - minY) * h) + 20);
            return new int[]{x, y, Math.max(8, cw), Math.max(8, ch)};
        }

        private float radius(ProfileStore.Binding b) {
            return 46f * profile.buttonSize * (b.size <= 0 ? 1f : b.size);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            /* ---- mapped controls ---- */
            for (int i = 0; i < bindings.size(); i++) {
                ProfileStore.Binding b = bindings.get(i);
                float x = b.x * getWidth(), y = b.y * getHeight(), r = radius(b);
                int alpha = (int) (255 * profile.buttonOpacity);
                Paint p = new Paint(dot);
                p.setColor(Color.argb((int) (alpha * 0.33), 34, 211, 238));
                c.drawCircle(x, y, r, p);
                Paint rp = new Paint(ring);
                rp.setColor(Color.argb(alpha, i == selected && editMode ? 168 : 34,
                        i == selected && editMode ? 85 : 211,
                        i == selected && editMode ? 247 : 238));
                rp.setStrokeWidth(i == selected && editMode ? 7f : 4f);
                c.drawCircle(x, y, r, rp);

                Paint tp = new Paint(label);
                tp.setColor(Color.argb(alpha, 255, 255, 255));
                tp.setTextSize(r * 0.55f);
                c.drawText(b.key, x, y + r * 0.18f, tp);
            }

            if (!editMode) return;

            /* ---- edit toolbar (ab use nahi hota — editMode hamesha false) ---- */
            float barH = 96, top = 26;
            rect.set(20, top, getWidth() - 20, top + barH);
            c.drawRoundRect(rect, 26, 26, bar);

            Paint tp = new Paint(label);
            tp.setTextSize(30f);
            tp.setColor(Color.WHITE);
            tp.setTextAlign(Paint.Align.LEFT);
            c.drawText("EDIT MODE — drag / tap to select", 44, top + 58, tp);

            float bx = getWidth() - 20;
            Paint btn = new Paint(label);
            btn.setTextAlign(Paint.Align.CENTER);
            btn.setTextSize(34f);

            // Done
            btn.setColor(0xFF22C55E);
            c.drawText("✓", bx - 48, top + 60, btn);
            // close
            btn.setColor(0xFFEF4444);
            c.drawText("✕", bx - 118, top + 60, btn);

            /* ---- selected actions ---- */
            if (selected >= 0 && selected < bindings.size()) {
                float ay = top + barH + 26;
                rect.set(20, ay, getWidth() - 20, ay + 96);
                Paint bar2 = new Paint(bar);
                bar2.setColor(0xF0101822);
                c.drawRoundRect(rect, 26, 26, bar2);
                Paint ap = new Paint(label);
                ap.setTextSize(30f);
                ap.setTextAlign(Paint.Align.LEFT);
                ap.setColor(0xFF9FB3C8);
                c.drawText(bindings.get(selected).key + " → " +
                        (bindings.get(selected).action == null || bindings.get(selected).action.isEmpty()
                                ? "—" : bindings.get(selected).action), 44, ay + 58, ap);

                Paint b2 = new Paint(btn);
                b2.setColor(0xFF22D3EE);
                c.drawText("−", getWidth() - 300, ay + 60, b2);
                c.drawText("+", getWidth() - 240, ay + 60, b2);
                b2.setColor(0xFFF59E0B);
                c.drawText("K", getWidth() - 176, ay + 60, b2);
                b2.setColor(0xFFEF4444);
                c.drawText("🗑", getWidth() - 96, ay + 60, b2);
            }

            /* ---- key chooser ---- */
            if (chooserOpen) {
                float gw = getWidth() - 40;
                float cell = gw / COLS;
                int rows = (KEY_CHOICES.length + COLS - 1) / COLS;
                float startY = Math.max(120, getHeight() / 2f - (rows * cell) / 2f);
                rect.set(20, startY - 40, getWidth() - 20, startY + rows * cell + 40);
                Paint bgp = new Paint(bar);
                bgp.setColor(0xF0070B12);
                c.drawRoundRect(rect, 26, 26, bgp);
                Paint kp = new Paint(label);
                kp.setTextSize(cell * 0.22f);
                kp.setColor(Color.WHITE);
                for (int i = 0; i < KEY_CHOICES.length; i++) {
                    int row = i / COLS, col = i % COLS;
                    float cx = 20 + col * cell + cell / 2f;
                    float cy = startY + row * cell + cell / 2f;
                    c.drawText(KEY_CHOICES[i], cx, cy, kp);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            float barH = 96, top = 26;

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downAt = System.currentTimeMillis();
                    downX = x; downY = y;

                    if (chooserOpen) {
                        float gw = getWidth() - 40, cell = gw / COLS;
                        int rows = (KEY_CHOICES.length + COLS - 1) / COLS;
                        float startY = Math.max(120, getHeight() / 2f - (rows * cell) / 2f);
                        int col = (int) ((x - 20) / cell);
                        int row = (int) ((y - startY) / cell);
                        int idx = row * COLS + col;
                        if (col >= 0 && col < COLS && row >= 0 && row < rows && idx < KEY_CHOICES.length) {
                            if (pendingBinding != null) pendingBinding.key = KEY_CHOICES[idx];
                            chooserOpen = false;
                            pendingBinding = null;
                            if (callback != null) callback.onChanged();
                            invalidate();
                            return true;
                        }
                        chooserOpen = false;
                        invalidate();
                        return true;
                    }

                    /* toolbar: done / close */
                    if (y < top + barH) {
                        if (x > getWidth() - 80) {                 // ✓ Done
                            if (callback != null) callback.onEditToggle();
                            return true;
                        }
                        if (x > getWidth() - 150 && x < getWidth() - 86) {   // ✕
                            if (callback != null) callback.onClose();
                            return true;
                        }
                    }

                    /* selected actions row */
                    if (selected >= 0 && selected < bindings.size()) {
                        float ay = top + barH + 26;
                        if (y > ay && y < ay + 96) {
                            ProfileStore.Binding b = bindings.get(selected);
                            if (x > getWidth() - 320 && x < getWidth() - 270) {
                                b.size = Math.max(0.5f, b.size - 0.15f);
                                if (callback != null) callback.onChanged();
                                invalidate();
                                return true;
                            }
                            if (x > getWidth() - 260 && x < getWidth() - 210) {
                                b.size = Math.min(2.0f, b.size + 0.15f);
                                if (callback != null) callback.onChanged();
                                invalidate();
                                return true;
                            }
                            if (x > getWidth() - 196 && x < getWidth() - 140) {
                                chooserOpen = true;
                                pendingBinding = b;
                                invalidate();
                                return true;
                            }
                            if (x > getWidth() - 116) {
                                bindings.remove(selected);
                                selected = -1;
                                if (callback != null) callback.onChanged();
                                invalidate();
                                return true;
                            }
                        }
                    }

                    /* pick a control */
                    for (int i = bindings.size() - 1; i >= 0; i--) {
                        ProfileStore.Binding b = bindings.get(i);
                        float dx = b.x * getWidth() - x, dy = b.y * getHeight() - y;
                        if (Math.sqrt(dx * dx + dy * dy) < radius(b) + 14) {
                            selected = i;
                            dragIndex = i;
                            dragDX = x - b.x * getWidth();
                            dragDY = y - b.y * getHeight();
                            invalidate();
                            return true;
                        }
                    }

                    /* empty area: naya button yahan lagao */
                    if (y > top + barH + 130) {
                        ProfileStore.Binding nb = new ProfileStore.Binding("NEW",
                                x / getWidth(), y / getHeight(), "");
                        bindings.add(nb);
                        selected = bindings.size() - 1;
                        chooserOpen = true;
                        pendingBinding = nb;
                        if (callback != null) callback.onChanged();
                        invalidate();
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (dragIndex >= 0 && dragIndex < bindings.size()) {
                        ProfileStore.Binding b = bindings.get(dragIndex);
                        b.x = Math.max(0.02f, Math.min(0.98f, (x - dragDX) / getWidth()));
                        b.y = Math.max(0.02f, Math.min(0.98f, (y - dragDY) / getHeight()));
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (dragIndex >= 0) {
                        if (callback != null) callback.onChanged();
                        dragIndex = -1;
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private ProfileStore.Binding find(String key) {
            for (ProfileStore.Binding b : bindings) if (b.key.equals(key)) return b;
            return null;
        }

        @Override
        public boolean onKeyDown(int code, KeyEvent event) {
            if (editMode) return super.onKeyDown(code, event);
            String name = keyName(code);
            if (name != null && callback != null) {
                callback.onKey(name);
                return true;
            }
            return super.onKeyDown(code, event);
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent e) {
            if (editMode) return super.onGenericMotionEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
                int b = e.getButtonState();
                String name = null;
                if ((b & MotionEvent.BUTTON_PRIMARY) != 0) name = "MOUSE_LEFT";
                else if ((b & MotionEvent.BUTTON_SECONDARY) != 0) name = "MOUSE_RIGHT";
                else if ((b & MotionEvent.BUTTON_TERTIARY) != 0) name = "MOUSE_MIDDLE";
                if (name != null && callback != null) { callback.onKey(name); return true; }
            }
            if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (callback != null) callback.onKey(v > 0 ? "SCROLL_UP" : "SCROLL_DOWN");
                return true;
            }
            return super.onGenericMotionEvent(e);
        }

        private String keyName(int code) {
            if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z)
                return String.valueOf((char) ('A' + (code - KeyEvent.KEYCODE_A)));
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
                return String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_0)));
            switch (code) {
                case KeyEvent.KEYCODE_SPACE: return "SPACE";
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT: return "SHIFT";
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT: return "CTRL";
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT: return "ALT";
                case KeyEvent.KEYCODE_ENTER: return "ENTER";
                case KeyEvent.KEYCODE_TAB: return "TAB";
                case KeyEvent.KEYCODE_ESCAPE: return "ESC";
                case KeyEvent.KEYCODE_DPAD_UP: return "UP";
                case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
                case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
                case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
                default: return null;
            }
        }
    }

    /* ===================== key capture window ===================== */
    static class KeySinkView extends View {
        interface Callback { void onKey(String key); }
        Callback callback;

        KeySinkView(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        @Override
        public boolean onKeyDown(int code, KeyEvent event) {
            String n = name(code);
            if (n != null && callback != null) { callback.onKey(n); return true; }
            return super.onKeyDown(code, event);
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
                int b = e.getButtonState();
                String n = null;
                if ((b & MotionEvent.BUTTON_PRIMARY) != 0) n = "MOUSE_LEFT";
                else if ((b & MotionEvent.BUTTON_SECONDARY) != 0) n = "MOUSE_RIGHT";
                else if ((b & MotionEvent.BUTTON_TERTIARY) != 0) n = "MOUSE_MIDDLE";
                if (n != null && callback != null) { callback.onKey(n); return true; }
            }
            return super.onGenericMotionEvent(e);
        }

        private String name(int code) {
            if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z)
                return String.valueOf((char) ('A' + (code - KeyEvent.KEYCODE_A)));
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
                return String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_0)));
            switch (code) {
                case KeyEvent.KEYCODE_SPACE: return "SPACE";
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT: return "SHIFT";
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT: return "CTRL";
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT: return "ALT";
                case KeyEvent.KEYCODE_TAB: return "TAB";
                case KeyEvent.KEYCODE_ESCAPE: return "ESC";
                default: return null;
            }
        }
    }

    /* ===================== joystick (gol circle) ===================== */
    static class JoystickView extends View {
        interface Callback {
            void onMove(float dx, float dy);
            void onRelease();
            void onPosition(float nx, float ny);
            void onStart();
        }
        Callback callback;
        private final ProfileStore.Profile profile;
        private final int screenW, screenH;
        private final int radius;
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float kx, ky;
        private boolean dragging = false, movingKnob = false;
        private float downRawX, downRawY;
        private long lastSend = 0;

        JoystickView(Context c, ProfileStore.Profile p, int w, int h) {
            super(c);
            profile = p; screenW = w; screenH = h;
            radius = (int) Math.max(70, p.joyR * w);
            base.setColor(0x66101822);
            ring.setColor(0xCC22D3EE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(5f);
            knob.setColor(0xE622D3EE);
            txt.setColor(0xCCFFFFFF);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(radius * 0.22f);
            kx = 0; ky = 0;
        }

        @Override
        protected void onMeasure(int wspec, int hspec) {
            setMeasuredDimension(radius * 2, radius * 2);
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = radius;
            c.drawCircle(r, r, r - 6, base);
            c.drawCircle(r, r, r - 6, ring);
            c.drawCircle(r + kx, r + ky, r * 0.42f, knob);
            Paint p = new Paint(txt);
            c.drawText("MOVE", r, r * 2 - 14, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            float r = radius;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX(); downRawY = e.getRawY();
                    dragging = true; movingKnob = false;
                    if (callback != null) callback.onStart();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - r, dy = e.getY() - r;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (!movingKnob && dist > r * 0.75f) movingKnob = true;
                    if (movingKnob) {
                        float max = r * 0.62f;
                        if (dist > max) { dx = dx / dist * max; dy = dy / dist * max; }
                        kx = dx; ky = dy;
                        invalidate();
                        long now = System.currentTimeMillis();
                        if (now - lastSend > 110) {
                            lastSend = now;
                            if (callback != null) callback.onMove(kx / max, ky / max);
                        }
                    } else if (dragging) {
                        // poora joystick shift karo
                        lp.x = (int) (e.getRawX() - r);
                        lp.y = (int) (e.getRawY() - r);
                        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) { }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (movingKnob && callback != null) callback.onRelease();
                    if (!movingKnob && callback != null) {
                        callback.onPosition(lp.x / (float) screenW, lp.y / (float) screenH);
                    }
                    kx = 0; ky = 0; invalidate();
                    dragging = false; movingKnob = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /* ===================== FIRE button ===================== */
    static class FireButtonView extends View {
        interface Callback {
            void onFireDown();
            void onMoved(float nx, float ny);
        }
        Callback callback;
        private final ProfileStore.Profile profile;
        private final int radius;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean dragging = false;
        private long downAt;

        FireButtonView(Context c, ProfileStore.Profile p) {
            super(c);
            profile = p;
            radius = (int) Math.max(34, Math.min(70, p.fireR * c.getResources().getDisplayMetrics().widthPixels));
            fill.setColor(0x99EF4444);
            ring.setColor(0xFFEF4444);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(5f);
            txt.setColor(0xFFFFFFFF);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setFakeBoldText(true);
            txt.setTextSize(radius * 0.34f);
        }

        @Override
        protected void onMeasure(int ws, int hs) { setMeasuredDimension(radius * 2, radius * 2); }

        @Override
        protected void onDraw(Canvas c) {
            float r = radius;
            c.drawCircle(r, r, r - 6, fill);
            c.drawCircle(r, r, r - 6, ring);
            c.drawText("FIRE", r, r + radius * 0.14f, txt);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downAt = System.currentTimeMillis();
                    dragging = false;
                    if (callback != null) callback.onFireDown();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (System.currentTimeMillis() - downAt > 500) {
                        dragging = true;
                        lp.x = (int) (e.getRawX() - radius);
                        lp.y = (int) (e.getRawY() - radius);
                        try { wm.updateViewLayout(this, lp); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging && callback != null) {
                        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                        callback.onMoved(lp.x / (float) dm.widthPixels, lp.y / (float) dm.heightPixels);
                    }
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /* ===================== mouse aim pad ===================== */
    static class AimPadView extends View {
        interface Callback {
            void onMove(float dx, float dy);
            void onFire();
        }
        Callback callback;
        private float lastX = -1, lastY = -1;
        private long lastSend = 0;
        private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AimPadView(Context c, ProfileStore.Profile p, int w, int h) {
            super(c);
            setBackgroundColor(Color.TRANSPARENT);
            hint.setColor(0x33FFFFFF);
            hint.setTextSize(28f);
            hint.setTextAlign(Paint.Align.CENTER);
        }

        private float curX = -1, curY = -1;
        private final Paint cursor = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawText("AIM", getWidth() / 2f, 60, hint);
            if (curX >= 0) {
                cursor.setColor(0xFF22D3EE);
                cursor.setStyle(Paint.Style.STROKE);
                cursor.setStrokeWidth(3f);
                c.drawCircle(curX, curY, 22, cursor);
                c.drawLine(curX - 32, curY, curX - 10, curY, cursor);
                c.drawLine(curX + 10, curY, curX + 32, curY, cursor);
                c.drawLine(curX, curY - 32, curX, curY - 10, cursor);
                c.drawLine(curX, curY + 10, curX, curY + 32, cursor);
            }
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE) {
                float x = e.getX(), y = e.getY();
                if (lastX >= 0) {
                    float dx = x - lastX, dy = y - lastY;
                    long now = System.currentTimeMillis();
                    if (Math.abs(dx) + Math.abs(dy) > 1.5f && now - lastSend > 35) {
                        lastSend = now;
                        if (callback != null) callback.onMove(dx, dy);
                    }
                }
                lastX = x; lastY = y;
                curX = x; curY = y;
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_BUTTON_PRESS) {
                if (callback != null) callback.onFire();
                return true;
            }
            return super.onGenericMotionEvent(e);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (callback != null) callback.onFire();
                return true;
            }
            return super.onTouchEvent(e);
        }
    }


    /* ===================== ek control button (apni window) ===================== */
    static class ControlView extends View {
        interface Callback {
            void onTap(ProfileStore.Binding b);
            void onMoved(ProfileStore.Binding b);
            void onLongPress(ProfileStore.Binding b);
        }
        Callback callback;
        WindowManager.LayoutParams params;
        private final ProfileStore.Profile profile;
        private final ProfileStore.Binding binding;
        private final int radius;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long downAt;
        private boolean dragged;

        ControlView(Context c, ProfileStore.Profile p, ProfileStore.Binding b) {
            super(c);
            profile = p;
            binding = b;
            radius = (int) Math.max(30, Math.min(64, 44 * p.buttonSize * (b.size <= 0 ? 1 : b.size)));
            fill.setColor(0x5522D3EE);
            ring.setColor(0xCC22D3EE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(4f);
            txt.setColor(0xFFFFFFFF);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(radius * 0.6f);
            setAlpha(0.95f);
        }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(radius * 2, radius * 2); }

        @Override
        protected void onDraw(Canvas c) {
            float r = radius;
            c.drawCircle(r, r, r - 5, fill);
            c.drawCircle(r, r, r - 5, ring);
            c.drawText(binding.key, r, r + radius * 0.2f, txt);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downAt = System.currentTimeMillis();
                    dragged = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (System.currentTimeMillis() - downAt > 350) {
                        dragged = true;
                        if (params != null) {
                            params.x = (int) (e.getRawX() - radius);
                            params.y = (int) (e.getRawY() - radius);
                            try { wm.updateViewLayout(this, params); } catch (Exception ignored) { }
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragged) {
                        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                        binding.x = Math.max(0.02f, Math.min(0.98f, (params.x + radius) / (float) dm.widthPixels));
                        binding.y = Math.max(0.02f, Math.min(0.98f, (params.y + radius) / (float) dm.heightPixels));
                        if (callback != null) callback.onMoved(binding);
                    } else if (System.currentTimeMillis() - downAt > 900) {
                        if (callback != null) callback.onLongPress(binding);
                    } else {
                        if (callback != null) callback.onTap(binding);
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

}
