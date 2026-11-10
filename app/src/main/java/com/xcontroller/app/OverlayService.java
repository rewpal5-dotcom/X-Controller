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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

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
    private WindowManager.LayoutParams panelParams, bubbleParams, joyParams, aimParams;

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
        panel = new PanelView(this, profile, w, h);
        panel.callback = new PanelView.Callback() {
            public void onKey(String key) { handleKey(key); }
            public void onEditToggle() { setEditMode(!editMode); }
            public void onClose() { stopSelf(); }
            public void onKeyToggle() { keyMode = !keyMode; panel.keyMode = keyMode; panel.invalidate(); }
            public void onChanged() { store.upsert(profile); }
        };

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Panel sirf controls ke aas-paas ka chhota area leta hai (poori screen nahi) —
        // isse game freeze/pause nahi hota.
        int[] box = panel.contentBox();
        panelParams = new WindowManager.LayoutParams(
                box[2], box[3],
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = box[0];
        panelParams.y = box[1];

        try { wm.addView(panel, panelParams); } catch (Exception ignored) { }

        /* ---- floating bubble ---- */
        bubble = new BubbleView(this);
        bubble.keysOn = profile.keyCapture;
        bubble.callback = new BubbleView.Callback() {
            public void onTap() { setEditMode(!editMode); }
            public void onLongPress() { setKeyCapture(!profile.keyCapture); }
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
        }

        /* ---- mouse aim pad (mouse hilane par camera ghumana) ---- */
        if (profile.aimEnabled) {   // default OFF — full-screen touch layer games ko rok deta hai
            aimPad = new AimPadView(this, profile, w, h);
            aimPad.callback = new AimPadView.Callback() {
                public void onMove(float dx, float dy) { aimBy(dx, dy); }
                public void onFire() { handleKey("MOUSE_LEFT"); }
            };
            aimParams = new WindowManager.LayoutParams(
                    profile.aimZone.equals("full") ? WindowManager.LayoutParams.MATCH_PARENT : (int) (w * 0.62f),
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            aimParams.gravity = Gravity.TOP | Gravity.END;
            try { wm.addView(aimPad, aimParams); } catch (Exception ignored) { }
        }
    }

    /* ---- joystick movement: game ke joystick par stroke bhejo ---- */
    private boolean joyActive = false;

    private void moveBy(float dx, float dy) {
        if (profile == null) return;
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = profile.joyX * dm.widthPixels;
        float cy = profile.joyY * dm.heightPixels;
        float r = profile.joyR * dm.widthPixels;
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
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float sens = Math.max(0.4f, profile.aimSensitivity / 50f);
        float cx = dm.widthPixels * 0.72f;
        float cy = dm.heightPixels * 0.45f;
        float sx = cx - dx * 2.2f * sens;
        float sy = cy - dy * 2.2f * sens;
        sx = Math.max(dm.widthPixels * 0.55f, Math.min(dm.widthPixels - 8, sx));
        sy = Math.max(8, Math.min(dm.heightPixels - 8, sy));
        svc.swipe(cx, cy, sx, sy, 55);
    }

    /* ---- keyboard capture window (sirf jab user chahe) ---- */
    private KeySinkView keySink;
    private WindowManager.LayoutParams keyParams;

    private void setKeyCapture(boolean on) {
        profile.keyCapture = on;
        store.upsert(profile);
        if (on && keySink == null) {
            keySink = new KeySinkView(this);
            keySink.callback = new KeySinkView.Callback() {
                public void onKey(String k) { handleKey(k); }
            };
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            keyParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            keyParams.gravity = Gravity.TOP | Gravity.START;
            keyParams.x = 0; keyParams.y = 0;
            try { wm.addView(keySink, keyParams); } catch (Exception ignored) { }
        } else if (!on && keySink != null) {
            try { wm.removeView(keySink); } catch (Exception ignored) { }
            keySink = null;
        }
        if (bubble != null) { bubble.keysOn = on; bubble.invalidate(); }
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
                panelParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                panelParams.height = WindowManager.LayoutParams.MATCH_PARENT;
                panelParams.x = 0; panelParams.y = 0;
            } else {
                int[] box = panel.contentBox();
                panelParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                panelParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                panelParams.width = box[2];
                panelParams.height = box[3];
                panelParams.x = box[0]; panelParams.y = box[1];
            }
            try { wm.updateViewLayout(panel, panelParams); } catch (Exception ignored) { }
        }
        if (!on) {
            store.upsert(profile);
        }
    }

    private void handleKey(String keyName) {
        if (editMode || !keyMode || profile == null) return;
        ProfileStore.Binding b = profile.find(keyName);
        if (b == null) return;
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        svc.tap(b.x * dm.widthPixels, b.y * dm.heightPixels);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("stop", false)) stopSelf();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) { }
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) { }
        try { if (joystick != null) wm.removeView(joystick); } catch (Exception ignored) { }
        try { if (aimPad != null) wm.removeView(aimPad); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /* ===================== floating bubble ===================== */
    static class BubbleView extends View {
        interface Callback { void onTap(); void onLongPress(); }
        Callback callback;
        boolean keysOn = false;
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
            txt.setColor(keysOn ? 0xFF22C55E : 0xFF22D3EE);
            ring.setColor(keysOn ? 0xFF22C55E : 0xFF22D3EE);
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
        private String pendingKeyTarget = null;

        private static final String[] KEY_CHOICES = {
                "W", "A", "S", "D", "SPACE", "SHIFT", "CTRL", "ALT",
                "R", "F", "E", "Q", "TAB", "ESC", "1", "2",
                "MOUSE_LEFT", "MOUSE_RIGHT", "SCROLL_UP", "SCROLL_DOWN"
        };

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

            /* ---- edit toolbar ---- */
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
                float gw = getWidth() - 80;
                float cell = gw / 4f;
                float startY = getHeight() / 2f - cell * 2.5f;
                rect.set(40, startY - 40, getWidth() - 40, startY + cell * 5 + 40);
                Paint bgp = new Paint(bar);
                bgp.setColor(0xF0070B12);
                c.drawRoundRect(rect, 26, 26, bgp);
                Paint kp = new Paint(label);
                kp.setTextSize(cell * 0.24f);
                kp.setColor(Color.WHITE);
                for (int i = 0; i < KEY_CHOICES.length; i++) {
                    int row = i / 4, col = i % 4;
                    float cx = 40 + col * cell + cell / 2f;
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
                        float gw = getWidth() - 80, cell = gw / 4f;
                        float startY = getHeight() / 2f - cell * 2.5f;
                        int col = (int) ((x - 40) / cell);
                        int row = (int) ((y - startY) / cell);
                        int idx = row * 4 + col;
                        if (col >= 0 && col < 4 && row >= 0 && row < 5 && idx < KEY_CHOICES.length) {
                            if (pendingKeyTarget != null) {
                                ProfileStore.Binding b = find(pendingKeyTarget);
                                if (b != null) b.key = KEY_CHOICES[idx];
                            }
                            chooserOpen = false;
                            pendingKeyTarget = null;
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
                                pendingKeyTarget = b.key;
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
                        pendingKeyTarget = nb.key;
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

}
