package com.xcontroller.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** About / Help / Privacy / Terms / Compatibility. */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box = UiKit.column(this);

        box.addView(UiKit.title(this, "X Controller", 24));
        box.addView(UiKit.muted(this, "Universal Keyboard & Mouse Gaming · v2.0"));

        box.addView(section("Kaise chalayein",
                "1. OTG ya Bluetooth se keyboard/mouse jodein\n"
              + "2. Home par permissions ✅ karein (Floating panel + Accessibility)\n"
              + "3. My Games → game chunein → Custom Controls mein keys set karein\n"
              + "4. Controller Profiles mein buttons ki jagah/size set karein\n"
              + "5. Start Controller dabayein, phir game kholein\n"
              + "6. Screen par chhota 'X' floating button — jahan chahein drag karein\n"
              + "7. Button par tap → EDIT mode: controls drag karke set karein, khaali jagah par\n"
              + "   tap se naya button, select karke size/key/delete, phir ✓ Done"));

        box.addView(section("Compatibility (imaandar report)",
                "• Native keyboard/mouse support wale games: poora support (game khud handle karta hai)\n"
              + "• Baaki games: Accessibility gesture mapping — sirf un games mein jahan Android aur game ki policy ijazat deti hai\n"
              + "• Anti-cheat wale games (Free Fire, BGMI jaise): bahari mapping block ho sakti hai — ban ka risk user ka\n"
              + "• Mouse se camera ghumana: sirf un games mein jahan native mouse support hai\n"
              + "Kisi game ko 100% supported nahi likha gaya hai jab tak asli device par test na ho."));

        box.addView(section("Privacy Policy",
                "• Koi input record nahi hota\n"
              + "• Koi password ya personal data nahi padha jaata\n"
              + "• Koi screen recording nahi\n"
              + "• Koi microphone permission nahi\n"
              + "• Saara data sirf aapke phone mein rehta hai (SharedPreferences JSON)\n"
              + "• Koi data server par nahi bheja jaata\n"
              + "• Accessibility service sirf aapke mapped buttons ke gesture bhejne ke liye hai\n"
              + "• Anti-cheat bypass nahi kiya jaata"));

        box.addView(section("Terms",
                "Ye app un games ke liye hai jahan aapko bahari control use karne ki ijazat hai. "
              + "Kisi bhi game ke terms todne ya ban hone ki zimmedari user ki hai. "
              + "App 'as is' diya gaya hai, koi guarantee nahi."));

        box.addView(section("Data storage",
                "Profiles, layouts aur settings phone ki local storage mein JSON ke roop mein "
              + "save hote hain. App uninstall karne par ye data chala jaata hai. "
              + "Export/Import se profile clipboard ke through share kar sakte hain."));

        ScrollView sv = UiKit.scroll(this, box);
        setContentView(sv);
    }

    private LinearLayout section(String title, String body) {
        LinearLayout c = UiKit.card(this);
        c.addView(UiKit.title(this, title, 15));
        TextView t = UiKit.body(this, body, 13.5f, UiKit.TEXT);
        t.setPadding(0, 12, 0, 0);
        c.addView(t);
        return c;
    }
}
