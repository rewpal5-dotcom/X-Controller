package com.xcontroller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Controller Profiles — touch layout editor: drag, resize, delete, test mode. */
public class LayoutActivity extends Activity {

    private ProfileStore store;
    private ProfileStore.Profile profile;
    private CanvasView canvas;
    private TextView info;

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
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(UiKit.BG);

        LinearLayout top = UiKit.row(this);
        top.setPadding(30, 28, 30, 10);
        TextView t = UiKit.title(this, profile.name, 18);
        t.setLayoutParams(UiKit.weight(1f));
        top.addView(t);
        info = UiKit.muted(this, "");
        top.addView(info);
        wrap.addView(top);

        canvas = new CanvasView(this, profile);
        wrap.addView(canvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = UiKit.row(this);
        bottom.setPadding(24, 12, 24, 26);

        Button smaller = UiKit.button(this, "−", 0xFF1B2636, UiKit.TEXT);
        smaller.setOnClickListener(v -> resizeSelected(-0.15f));
        bottom.addView(smaller);
        bottom.addView(UiKit.spacer(this, 10, 1));

        Button bigger = UiKit.button(this, "+", 0xFF1B2636, UiKit.TEXT);
        bigger.setOnClickListener(v -> resizeSelected(0.15f));
        bottom.addView(bigger);
        bottom.addView(UiKit.spacer(this, 10, 1));

        Button test = UiKit.button(this, "Test", 0xFF123542, UiKit.ACCENT);
        test.setLayoutParams(UiKit.weight(1f));
        test.setOnClickListener(v -> {
            canvas.testMode = !canvas.testMode;
            canvas.invalidate();
            Toast.makeText(this, canvas.testMode ? "Test mode ON — button dabayein" : "Test mode OFF",
                    Toast.LENGTH_SHORT).show();
        });
        bottom.addView(test);
        bottom.addView(UiKit.spacer(this, 10, 1));

        Button save = UiKit.button(this, "Save", UiKit.ACCENT, 0xFF04121A);
        save.setLayoutParams(UiKit.weight(1f));
        save.setOnClickListener(v -> {
            store.upsert(profile);
            store.setActive(profile.name);
            Toast.makeText(this, "Layout save", Toast.LENGTH_SHORT).show();
            finish();
        });
        bottom.addView(save);

        wrap.addView(bottom);
        setContentView(wrap);
        updateInfo();
    }

    private void updateInfo() {
        if (info != null) info.setText(profile.bindings.size() + " controls");
    }

    private void resizeSelected(float delta) {
        if (canvas.selected < 0) { Toast.makeText(this, "Pehle koi button chunein", Toast.LENGTH_SHORT).show(); return; }
        ProfileStore.Binding b = profile.bindings.get(canvas.selected);
        b.size = Math.max(0.5f, Math.min(2.0f, b.size + delta));
        canvas.invalidate();
    }

    /* ---------------- canvas ---------------- */
    static class CanvasView extends View {
        private final ProfileStore.Profile profile;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        int selected = -1;
        boolean testMode = false;
        private long downAt = 0;
        private float downX, downY;

        CanvasView(Context c, ProfileStore.Profile p) {
            super(c);
            profile = p;
            fill.setColor(0x5512B7C9);
            ring.setColor(UiKit.ACCENT);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(4f);
            txt.setColor(Color.WHITE);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(30f);
            grid.setColor(0x12FFFFFF);
            grid.setStrokeWidth(2f);
            setBackgroundColor(0xFF04070C);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            for (int i = 1; i < 10; i++) {
                c.drawLine(w * i / 10f, 0, w * i / 10f, h, grid);
                c.drawLine(0, h * i / 10f, w, h * i / 10f, grid);
            }
            float base = 54f * profile.buttonSize;
            for (int i = 0; i < profile.bindings.size(); i++) {
                ProfileStore.Binding b = profile.bindings.get(i);
                float cx = b.x * w, cy = b.y * h, rad = base * b.size;
                c.drawCircle(cx, cy, rad, fill);
                Paint p = new Paint(ring);
                if (i == selected) { p.setColor(UiKit.PURPLE); p.setStrokeWidth(7f); }
                c.drawCircle(cx, cy, rad, p);
                Paint tp = new Paint(txt);
                tp.setTextSize(rad * 0.62f);
                c.drawText(b.key, cx, cy + rad * 0.22f, tp);
                if (b.action != null && !b.action.isEmpty()) {
                    Paint ap = new Paint(txt);
                    ap.setTextSize(rad * 0.34f);
                    ap.setColor(0xFFB9C6D8);
                    c.drawText(b.action, cx, cy + rad * 0.85f, ap);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int w = getWidth(), h = getHeight();
            float base = 54f * profile.buttonSize;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downAt = System.currentTimeMillis();
                    downX = e.getX(); downY = e.getY();
                    selected = -1;
                    for (int i = profile.bindings.size() - 1; i >= 0; i--) {
                        ProfileStore.Binding b = profile.bindings.get(i);
                        float dx = b.x * w - e.getX(), dy = b.y * h - e.getY();
                        if (Math.sqrt(dx * dx + dy * dy) < base * b.size + 18) { selected = i; break; }
                    }
                    if (testMode && selected >= 0) {
                        ProfileStore.Binding b = profile.bindings.get(selected);
                        KeyAccessibilityService svc = KeyAccessibilityService.get();
                        if (svc != null) svc.tap(b.x * w, b.y * h);
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (!testMode && selected >= 0) {
                        ProfileStore.Binding b = profile.bindings.get(selected);
                        b.x = Math.max(0.03f, Math.min(0.97f, e.getX() / w));
                        b.y = Math.max(0.03f, Math.min(0.97f, e.getY() / h));
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!testMode && selected >= 0
                            && System.currentTimeMillis() - downAt > 600
                            && Math.abs(e.getX() - downX) < 18 && Math.abs(e.getY() - downY) < 18) {
                        profile.bindings.remove(selected);
                        selected = -1;
                        invalidate();
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
