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
 * OverlayService — game ke upar chalta hua floating controller.
 *
 * Kaam:
 *  1. Game ke upar chhota panel dikhana (jo hilaya ja sakta hai)
 *  2. Keyboard ki keys pakadna (jab "KEY" mode on ho)
 *  3. Har key ko profile ke point par tap gesture mein badalna
 */
public class OverlayService extends Service {

    public static boolean running = false;
    private static OverlayService instance;

    private WindowManager wm;
    private ControllerView view;
    private WindowManager.LayoutParams params;
    private ProfileStore.Profile profile;
    private boolean keyMode = true;

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
                ch.setDescription("Floating controller chal raha hai");
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(this, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        b.setContentTitle("X Controller chal raha hai")
                .setContentText("Floating panel screen par hai — band karne ke liye ✕ dabayein")
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
        int w = dm.widthPixels;
        int h = dm.heightPixels;

        view = new ControllerView(this, profile, w, h);
        view.setKeyListener(new ControllerView.KeyListener() {
            @Override
            public void onKey(String keyName) {
                handleKey(keyName);
            }
            @Override
            public void onClose() { stopSelf(); }
            @Override
            public void onToggleKeyMode() {
                keyMode = !keyMode;
                view.setKeyMode(keyMode);
            }
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                // focusable (key events pane) + NOT_TOUCH_MODAL (baaki screen game ko jaaye)
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        try {
            wm.addView(view, params);
        } catch (Exception ignored) { }
    }

    private void handleKey(String keyName) {
        if (!keyMode) return;
        if (profile == null) return;
        ProfileStore.Binding b = profile.find(keyName);
        if (b == null) return;
        KeyAccessibilityService svc = KeyAccessibilityService.get();
        if (svc == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = b.x * dm.widthPixels;
        float y = b.y * dm.heightPixels;
        svc.tap(x, y);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("stop", false)) {
            stopSelf();
        }
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

    /* ==================== floating view ==================== */
    public static class ControllerView extends FrameLayout {

        public interface KeyListener {
            void onKey(String keyName);
            void onClose();
            void onToggleKeyMode();
        }

        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final List<ProfileStore.Binding> bindings = new ArrayList<>();
        private final int screenW, screenH;
        private float handleX = 40, handleY = 140;
        private boolean draggingHandle = false;
        private float dragDX, dragDY;
        private boolean keyMode = true;
        private KeyListener listener;

        public ControllerView(Context ctx, ProfileStore.Profile profile, int w, int h) {
            super(ctx);
            screenW = w;
            screenH = h;
            if (profile != null) bindings.addAll(profile.bindings);
            setWillNotDraw(false);
            setFocusable(true);
            setFocusableInTouchMode(true);

            dot.setColor(Color.parseColor("#6622D3EE"));
            dot.setStyle(Paint.Style.FILL);
            label.setColor(Color.parseColor("#CC22D3EE"));
            label.setStyle(Paint.Style.STROKE);
            label.setStrokeWidth(3f);
            panel.setColor(Color.parseColor("#CC0B0F17"));
            text.setColor(Color.WHITE);
            text.setTextSize(34f);
            text.setAntiAlias(true);
        }

        public void setKeyListener(KeyListener l) { listener = l; }

        public void setKeyMode(boolean on) { keyMode = on; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float r = 46f;

            // mapped points
            for (ProfileStore.Binding b : bindings) {
                float x = b.x * getWidth();
                float y = b.y * getHeight();
                c.drawCircle(x, y, r, dot);
                c.drawCircle(x, y, r, label);
                Paint p = new Paint(text);
                p.setTextSize(28f);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(b.key, x, y + 10f, p);
            }

            // draggable handle
            rect.set(handleX, handleY, handleX + 240, handleY + 96);
            c.drawRoundRect(rect, 26, 26, panel);
            Paint hp = new Paint(text);
            hp.setTextSize(34f);
            hp.setColor(Color.parseColor("#22D3EE"));
            c.drawText("✥ X", handleX + 20, handleY + 60, hp);
            hp.setColor(keyMode ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"));
            hp.setTextSize(26f);
            c.drawText(keyMode ? "KEY ON" : "KEY OFF", handleX + 110, handleY + 58, hp);
            hp.setColor(Color.parseColor("#EF4444"));
            hp.setTextSize(34f);
            c.drawText("✕", handleX + 205, handleY + 60, hp);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (x >= handleX && x <= handleX + 240 && y >= handleY && y <= handleY + 96) {
                        if (x > handleX + 185) {              // ✕ close
                            if (listener != null) listener.onClose();
                        } else if (x > handleX + 100) {       // KEY toggle
                            if (listener != null) listener.onToggleKeyMode();
                        } else {                              // drag
                            draggingHandle = true;
                            dragDX = x - handleX;
                            dragDY = y - handleY;
                        }
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (draggingHandle) {
                        handleX = Math.max(0, Math.min(screenW - 240, x - dragDX));
                        handleY = Math.max(0, Math.min(screenH - 96, y - dragDY));
                        invalidate();
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    draggingHandle = false;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
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

        private String keyName(int code) {
            if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) {
                return String.valueOf((char) ('A' + (code - KeyEvent.KEYCODE_A)));
            }
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                return String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_0)));
            }
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
                default: return null;
            }
        }
    }
}
