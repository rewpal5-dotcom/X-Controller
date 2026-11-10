package com.xcontroller.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.InputDevice;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Compatibility Test — asli diagnosis.
 * Batata hai: accessibility chalu hai?, overlay permission?, keyboard/mouse mile?,
 * aur gesture bhejne par game ne accept kiya ya block kiya.
 */
public class DiagActivity extends Activity {

    private TextView status, report, devices;
    private final Handler h = new Handler();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout box = UiKit.column(this);
        box.addView(UiKit.title(this, "Compatibility Test", 22));
        box.addView(UiKit.muted(this, "Yahan sab kuch live dikhega — asli diagnosis"));

        LinearLayout c1 = UiKit.card(this);
        c1.addView(UiKit.title(this, "Permissions", 15));
        status = UiKit.body(this, "", 13.5f, UiKit.TEXT);
        status.setPadding(0, 12, 0, 0);
        c1.addView(status);
        box.addView(c1);

        LinearLayout c2 = UiKit.card(this);
        c2.addView(UiKit.title(this, "Devices", 15));
        devices = UiKit.body(this, "", 13.5f, UiKit.TEXT);
        devices.setPadding(0, 12, 0, 0);
        c2.addView(devices);
        box.addView(c2);

        LinearLayout c3 = UiKit.card(this);
        c3.addView(UiKit.title(this, "Gesture result", 15));
        report = UiKit.body(this, "", 13.5f, UiKit.TEXT);
        report.setPadding(0, 12, 0, 0);
        c3.addView(report);

        Button test = UiKit.button(this, "▶ Test tap bhejein (screen ke beech)", UiKit.ACCENT, 0xFF04121A);
        test.setOnClickListener(v -> {
            KeyAccessibilityService svc = KeyAccessibilityService.get();
            if (svc == null) {
                report.setText("Accessibility service chalu nahi hai — pehle ON karein.");
                return;
            }
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            svc.tap(dm.widthPixels / 2f, dm.heightPixels / 2f);
            report.setText("Tap bheja… result ka intezaar…");
        });
        c3.addView(test, UiKit.matchWrap(14));
        box.addView(c3);

        LinearLayout c4 = UiKit.card(this);
        c4.addView(UiKit.title(this, "Agar game gesture block kare", 15));
        c4.addView(UiKit.body(this,
                "• Kuch games (anti-cheat) accessibility gestures ignore karte hain —\n"
              + "  unmein 'block hua' count badhta rahega.\n"
              + "• Aise game mein keyboard/joystick mapping kaam nahi karegi (Android ki\n"
              + "  security limit hai, koi app bypass nahi kar sakta).\n"
              + "• Un games mein native keyboard/mouse support hi kaam karta hai.",
                13.5f, UiKit.MUTED));
        Button fix = UiKit.button(this, "Accessibility settings kholein", 0xFF1B2636, UiKit.TEXT);
        fix.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        c4.addView(fix, UiKit.matchWrap(14));
        Button ov = UiKit.button(this, "Overlay permission", 0xFF1B2636, UiKit.TEXT);
        ov.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        c4.addView(ov, UiKit.matchWrap(10));
        box.addView(c4);

        ScrollView sv = UiKit.scroll(this, box);
        setContentView(sv);
        h.post(tick);
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            boolean a11y = KeyAccessibilityService.isRunning();
            boolean ov = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(DiagActivity.this);
            boolean svc = OverlayService.running;

            status.setText((a11y ? "✅" : "❌") + "  Accessibility (touch engine)\n"
                    + (ov ? "✅" : "❌") + "  Floating panel permission\n"
                    + (svc ? "✅" : "⚠️") + "  Controller service");

            StringBuilder d = new StringBuilder();
            boolean kb = false, ms = false;
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice dev = InputDevice.getDevice(id);
                if (dev == null) continue;
                int s = dev.getSources();
                if ((s & InputDevice.SOURCE_KEYBOARD) != 0) kb = true;
                if ((s & InputDevice.SOURCE_MOUSE) != 0 || (s & InputDevice.SOURCE_TOUCHPAD) != 0) ms = true;
                if ((s & InputDevice.SOURCE_KEYBOARD) != 0 || (s & InputDevice.SOURCE_MOUSE) != 0) {
                    d.append("• ").append(dev.getName()).append("\n");
                }
            }
            if (d.length() == 0) d.append("Koi keyboard/mouse nahi mila — OTG/Bluetooth se jodein");
            devices.setText((kb ? "⌨ keyboard ✅\n" : "⌨ keyboard ❌\n")
                    + (ms ? "🖱 mouse ✅\n\n" : "🖱 mouse ❌\n\n") + d);

            report.setText(KeyAccessibilityService.report());
            h.postDelayed(this, 800);
        }
    };

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }
}
