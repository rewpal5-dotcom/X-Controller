package com.xcontroller.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * TestActivity — Keyboard aur Mouse ka asli test.
 * Yahan jo bhi key dabayein ya mouse hilayein, turant screen par dikhta hai.
 */
public class TestActivity extends Activity {

    private TextView keyView, mouseView, moveView, scrollView, statusView;
    private float lastX = -1, lastY = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String mode = getIntent().getStringExtra("mode");

        LinearLayout box = UiKit.column(this);
        box.addView(UiKit.title(this, "Keyboard & Mouse Test", 22));
        box.addView(UiKit.muted(this, "Koi bhi key dabayein / mouse hilayein — yahan turant dikhega"));

        LinearLayout k = UiKit.card(this);
        k.addView(UiKit.title(this, "⌨  Keyboard", 15));
        keyView = UiKit.body(this, "Koi key nahi dabi…", 20f, UiKit.ACCENT);
        keyView.setPadding(0, 16, 0, 6);
        k.addView(keyView);
        statusView = UiKit.muted(this, "Keyboard: " + (MainActivity.hasKeyboard() ? "connected" : "not connected"));
        k.addView(statusView);
        box.addView(k);

        LinearLayout m = UiKit.card(this);
        m.addView(UiKit.title(this, "🖱  Mouse", 15));
        mouseView = UiKit.body(this, "Click: —", 18f, UiKit.PURPLE);
        mouseView.setPadding(0, 14, 0, 6);
        m.addView(mouseView);
        moveView = UiKit.body(this, "Move: x 0  y 0", 15f, UiKit.TEXT);
        m.addView(moveView);
        scrollView = UiKit.body(this, "Scroll: —", 15f, UiKit.TEXT);
        m.addView(scrollView);
        m.addView(UiKit.muted(this, "Mouse: " + (MainActivity.hasMouse() ? "connected" : "not connected")));
        box.addView(m);

        TextView note = UiKit.muted(this,
                "Note: ye screen app ke andar ka test hai. Doosre game ke andar keyboard "
                + "input Android seedhe nahi deta — uske liye Start Controller + Accessibility "
                + "mapping use hoti hai.");
        box.addView(note);

        ScrollView sv = UiKit.scroll(this, box);
        sv.setFocusableInTouchMode(true);
        sv.requestFocus();
        setContentView(sv);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        String name = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
        keyView.setText(name + "  ✅ Detected");
        statusView.setText("Keyboard: connected (key mila)");
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        int src = e.getSource();
        if ((src & InputDevice.SOURCE_MOUSE) != 0 || (src & InputDevice.SOURCE_TOUCHPAD) != 0) {
            if (e.getAction() == MotionEvent.ACTION_HOVER_MOVE || e.getAction() == MotionEvent.ACTION_MOVE) {
                lastX = e.getX();
                lastY = e.getY();
                moveView.setText("Move: x " + Math.round(lastX) + "  y " + Math.round(lastY));
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_SCROLL) {
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                scrollView.setText("Scroll: " + (v > 0 ? "UP ⬆" : "DOWN ⬇"));
                return true;
            }
        }
        return super.onGenericMotionEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mouseView.setText("Click: LEFT ✅  (x " + Math.round(e.getX()) + ", y " + Math.round(e.getY()) + ")");
            return true;
        }
        return super.onTouchEvent(e);
    }
}
