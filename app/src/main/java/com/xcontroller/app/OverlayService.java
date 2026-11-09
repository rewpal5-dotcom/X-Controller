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
 * OverlayService — game ke upar floating controller.
 * Panel: ✥ drag · KEY on/off · ✎ edit · ⏸ pause · ✕ close
 */
public class OverlayService extends Service {

    public static boolean running = false;
    private static OverlayService instance;

    private WindowManager wm;
    private PanelView view;
    private WindowManager.LayoutParams params;
    private ProfileStore.Profile profile;
    private boolean keyMode = true;
    private boolean paused = false;

    public static OverlayService get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
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
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        b.setContentTitle("X Controller chal raha hai")
                .setContentText("Floating panel screen par hai")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(7, b.build(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, b.build());
        }
    }

    private void showOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        ProfileStore store = new ProfileStore(this);
        profile = store.active();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;

        view = new PanelView(this, profile, w, h);
        view.listener = new PanelView.Listener() {
            public void onKey(String key) { handleKey(key); }
            public void onClose() { stopSelf(); }
            public void onToggle() { keyMode = !keyMode; view.keyMode = keyMode; view.invalidate(); }
            public void onPause() { paused = !paused; view.paused = paused; view.invalidate(); }
            public void onEdit() {
                Intent i = new Intent(OverlayService.this, LayoutActivity.class);
                i.putExtra("profile", profile == null ? "" : profile.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        };

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try { wm.addView(view, params); } catch (Exception ignored) { }
    }

    private void handleKey(String keyName) {
        if (!keyMode || paused || profile == null) return;
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
        if (view != null && wm != null) {
            try { wm.removeView(view); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /* ==================== floating panel ==================== */
    static class PanelView extends FrameLayout {

        interface Listener {
            void onKey(String key);
            void onClose();
            void onToggle();
            void onPause();
            void onEdit();
        }

        Listener listener;
        boolean keyMode = true;
        boolean paused = false;

        private final List<ProfileStore.Binding> bindings = new ArrayList<>();
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int screenW, screenH;
        private final float opacity, btnScale;
        private float barX = 24, barY = 130;
        private boolean draggingBar = false;
        private float dragDX, dragDY;

        PanelView(Context c, ProfileStore.Profile p, int w, int h) {
            super(c);
            screenW = w; screenH = h;
            if (p != null) {
                bindings.addAll(p.bindings);
                opacity = p.buttonOpacity;
                btnScale = p.buttonSize;
            } else {
                opacity = 0.85f; btnScale = 1f;
            }
            setWillNotDraw(false);
            setFocusable(true);
            setFocusableInTouchMode(true);

            int alpha = (int) (255 * opacity);
            dot.setColor(Color.argb((int) (alpha * 0.35), 34, 211, 238));
            ring.setColor(Color.argb(alpha, 34, 211, 238));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(4f);
            label.setColor(Color.argb(alpha, 255, 255, 255));
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(30f);
            panel.setColor(Color.argb(220, 7, 11, 18));
            chip.setColor(Color.argb(255, 34, 211, 238));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float base = 46f * btnScale;
            for (ProfileStore.Binding b : bindings) {
                float x = b.x * getWidth(), y = b.y * getHeight(), rad = base * b.size;
                c.drawCircle(x, y, rad, dot);
                c.drawCircle(x, y, rad, ring);
                Paint tp = new Paint(label);
                tp.setTextSize(rad * 0.6f);
                c.drawText(b.key, x, y + rad * 0.2f, tp);
            }

            // top bar
            rect.set(barX, barY, barX + 430, barY + 92);
            c.drawRoundRect(rect, 26, 26, panel);

            Paint p = new Paint(label);
            p.setTextSize(32f);
            p.setColor(UiKit.ACCENT);
            c.drawText("✥", barX + 42, barY + 60, p);

            p.setColor(keyMode ? UiKit.GREEN : UiKit.RED);
            p.setTextSize(24f);
            c.drawText(keyMode ? "KEY ON" : "KEY OFF", barX + 108, barY + 57, p);

            p.setColor(paused ? UiKit.AMBER : UiKit.TEXT);
            c.drawText(paused ? "⏸ PAUSED" : "⏸", barX + 250, barY + 57, p);

            p.setColor(UiKit.TEXT);
            c.drawText("✎", barX + 336, barY + 57, p);

            p.setColor(UiKit.RED);
            c.drawText("✕", barX + 398, barY + 57, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (x >= barX && x <= barX + 430 && y >= barY && y <= barY + 92) {
                        if (x > barX + 370) { if (listener != null) listener.onClose(); }
                        else if (x > barX + 300) { if (listener != null) listener.onEdit(); }
                        else if (x > barX + 210) { if (listener != null) listener.onPause(); }
                        else if (x > barX + 80) { if (listener != null) listener.onToggle(); }
                        else {
                            draggingBar = true;
                            dragDX = x - barX; dragDY = y - barY;
                        }
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (draggingBar) {
                        barX = Math.max(0, Math.min(screenW - 430, x - dragDX));
                        barY = Math.max(0, Math.min(screenH - 92, y - dragDY));
                        invalidate();
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    draggingBar = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            String name = keyName(keyCode);
            if (name != null && listener != null) {
                listener.onKey(name);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_BUTTON_PRESS) {
                int btn = e.getButtonState();
                String name = null;
                if ((btn & MotionEvent.BUTTON_PRIMARY) != 0) name = "MOUSE_LEFT";
                else if ((btn & MotionEvent.BUTTON_SECONDARY) != 0) name = "MOUSE_RIGHT";
                else if ((btn & MotionEvent.BUTTON_TERTIARY) != 0) name = "MOUSE_MIDDLE";
                if (name != null && listener != null) {
                    listener.onKey(name);
                    return true;
                }
            }
            if (action == MotionEvent.ACTION_SCROLL) {
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (listener != null) listener.onKey(v > 0 ? "SCROLL_UP" : "SCROLL_DOWN");
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
}
