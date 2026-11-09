package com.xcontroller.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * KeyAccessibilityService — X Controller ka touch engine.
 *
 * Jab overlay se koi mapped key dabti hai, ye service us point par
 * "tap" (ya hold) gesture bhej deti hai. Isi se game mein touch button
 * dabne jaisa asar hota hai.
 *
 * Note: Accessibility se sirf gesture bheja ja sakta hai — kuch games
 * (anti-cheat wale) ise block kar sakte hain.
 */
public class KeyAccessibilityService extends AccessibilityService {

    private static final String TAG = "XControllerA11y";
    private static KeyAccessibilityService instance;

    public static boolean isRunning() { return instance != null; }

    public static KeyAccessibilityService get() { return instance; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    /** Screen par ek tap bhejo (x, y pixels mein). */
    public void tap(float x, float y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 45);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        try {
            dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Log.w(TAG, "tap failed: " + e.getMessage());
        }
    }

    /** Screen par hold (jaise fire button dabaye rakhna). */
    public void hold(float x, float y, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(60, durationMs));
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        try {
            dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Log.w(TAG, "hold failed: " + e.getMessage());
        }
    }

    /** Do point ke beech swipe (jaise camera ghumana). */
    public void swipe(float x1, float y1, float x2, float y2, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(50, durationMs));
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        try {
            dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Log.w(TAG, "swipe failed: " + e.getMessage());
        }
    }
}
