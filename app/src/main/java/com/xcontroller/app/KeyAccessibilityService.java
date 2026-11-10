package com.xcontroller.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * KeyAccessibilityService — touch engine (v5).
 *
 * Ab har gesture ka result bhi milta hai (completed / cancelled),
 * jisse pata chalta hai ki game ne gesture accept kiya ya block kiya.
 * Joystick ke liye continuing-stroke use hota hai (smooth held movement).
 */
public class KeyAccessibilityService extends AccessibilityService {

    private static final String TAG = "XControllerA11y";
    private static KeyAccessibilityService instance;

    /* diagnostics — app ke Compatibility Test screen par dikhte hain */
    public static int sent = 0, completed = 0, cancelled = 0;
    public static String lastResult = "—";
    public static long lastAt = 0;

    private GestureDescription.StrokeDescription joyStroke;
    private float joyX, joyY;

    public static boolean isRunning() { return instance != null; }
    public static KeyAccessibilityService get() { return instance; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { instance = null; }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    private GestureResultCallback cb(final String label) {
        return new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                completed++;
                lastResult = label + ": completed ✅";
                lastAt = System.currentTimeMillis();
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                cancelled++;
                lastResult = label + ": cancelled ⛔ (game ne block kiya)";
                lastAt = System.currentTimeMillis();
            }
        };
    }

    private void send(GestureDescription g, String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        sent++;
        lastAt = System.currentTimeMillis();
        try {
            dispatchGesture(g, cb(label), null);
        } catch (Exception e) {
            lastResult = label + ": error " + e.getMessage();
        }
    }

    /** Screen par tap. */
    public void tap(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        send(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 40)).build(), "tap");
    }

    /** Hold (dabaye rakhna). */
    public void hold(float x, float y, long ms) {
        Path p = new Path();
        p.moveTo(x, y);
        send(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(60, ms))).build(), "hold");
    }

    /** Ek stroke (joystick ka purana tarika). */
    public void stroke(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        send(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(80, ms))).build(), "stroke");
    }

    /** Swipe (camera ghumana). */
    public void swipe(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        send(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(40, ms))).build(), "swipe");
    }

    /* ---------------- joystick: continuing stroke ---------------- */

    public void joyStart(float x, float y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        joyX = x; joyY = y;
        Path p = new Path();
        p.moveTo(x, y);
        joyStroke = new GestureDescription.StrokeDescription(p, 0, 120, true);
        send(new GestureDescription.Builder().addStroke(joyStroke).build(), "joystick-start");
    }

    public void joyMove(float x, float y) {
        if (joyStroke == null) return;
        joyX = x; joyY = y;
        Path p = new Path();
        p.moveTo(x, y);
        try {
            joyStroke = joyStroke.continueStroke(p, 0, 120, true);
            send(new GestureDescription.Builder().addStroke(joyStroke).build(), "joystick-move");
        } catch (Exception e) {
            joyStroke = null;
        }
    }

    public void joyEnd() {
        if (joyStroke == null) return;
        Path p = new Path();
        p.moveTo(joyX, joyY);
        try {
            GestureDescription.StrokeDescription end = joyStroke.continueStroke(p, 0, 1, false);
            joyStroke = null;
            send(new GestureDescription.Builder().addStroke(end).build(), "joystick-end");
        } catch (Exception e) {
            joyStroke = null;
        }
    }

    public static String report() {
        return "bheje: " + sent + "\npura hua: " + completed + "\nblock hua: " + cancelled
                + "\nlast: " + lastResult;
    }
}
