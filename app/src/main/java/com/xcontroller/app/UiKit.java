package com.xcontroller.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** UiKit — saare screens ke liye chhote UI helpers (premium gaming look). */
public class UiKit {

    public static final int BG = 0xFF070B12;
    public static final int CARD = 0xFF111A26;
    public static final int CARD2 = 0xFF16202E;
    public static final int ACCENT = 0xFF22D3EE;   // neon blue
    public static final int PURPLE = 0xFFA855F7;
    public static final int GREEN = 0xFF22C55E;
    public static final int RED = 0xFFEF4444;
    public static final int AMBER = 0xFFF59E0B;
    public static final int TEXT = 0xFFEAF2FF;
    public static final int MUTED = 0xFF8A97AC;
    public static final int LINE = 0xFF22303F;

    public static TextView title(Context c, String t, int size) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(TEXT);
        v.setTextSize(size);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    public static TextView body(Context c, String t, int size, int color) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setLineSpacing(8f, 1f);
        return v;
    }

    public static TextView muted(Context c, String t) {
        return body(c, t, 12.5f, MUTED);
    }

    public static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(14.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(bg, 26, 0));
        b.setPadding(26, 16, 26, 16);
        return b;
    }

    public static GradientDrawable round(int color, float radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeColor != 0) g.setStroke(2, strokeColor);
        return g;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 30, LINE));
        l.setPadding(34, 30, 34, 30);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 22;
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        l.setPadding(38, 34, 38, 38);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    public static View spacer(Context c, int w, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }

    public static ScrollView scroll(Context c, View content) {
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(content);
        return sv;
    }

    public static void divider(Context c, LinearLayout parent) {
        View v = new View(c);
        v.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2);
        lp.topMargin = 18;
        lp.bottomMargin = 18;
        parent.addView(v, lp);
    }
}
