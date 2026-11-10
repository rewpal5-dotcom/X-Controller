package com.xcontroller.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ProfileStore — saara data (profiles, layouts, settings) phone mein JSON ke roop
 * mein save karta hai. App band hone par bhi sab surakshit rehta hai.
 */
public class ProfileStore {

    public static class Binding {
        public String key;     // W, A, SPACE, MOUSE_LEFT ...
        public String action;  // Move Forward, Jump, Fire ...
        public float x, y;     // 0..1
        public float size = 1f;// 0.6 .. 1.8
        public Binding(String key, float x, float y) { this(key, x, y, ""); }
        public Binding(String key, float x, float y, String action) {
            this.key = key; this.x = x; this.y = y; this.action = action;
        }
    }

    public static class Profile {
        public String name = "Profile";
        public String gamePackage = "";
        public List<Binding> bindings = new ArrayList<>();
        public int sensitivity = 50;      // mouse sensitivity 1..100
        public int aimSensitivity = 50;
        public int pointerSpeed = 50;
        public boolean invertY = false;
        public float buttonSize = 1f;
        public float buttonOpacity = 0.85f;
        public String orientation = "auto";   // auto / portrait / landscape

        // virtual joystick (gol circle wala movement control)
        public boolean joystick = true;
        public float joyX = 0.16f, joyY = 0.78f;   // centre (0..1)
        public float joyR = 0.11f;                 // radius (screen width ka hissa)

        // mouse aim (mouse hilane par camera ghumana — swipe se)
        public boolean aimEnabled = true;
        public String aimZone = "right";           // right / full
        public int aimSensitivity = 50;

        public Profile() {}
        public Profile(String name) { this.name = name; }

        public Binding find(String key) {
            for (Binding b : bindings) if (b.key.equalsIgnoreCase(key)) return b;
            return null;
        }
    }

    private static final String PREFS = "xcontroller_v2";
    private static final String K_PROFILES = "profiles";
    private static final String K_ACTIVE = "active";
    private static final String K_DEFAULT = "default_profile";
    private static final String K_THEME = "theme";
    private static final String K_AUTOSTART = "autostart";
    private static final String K_DEBUG = "input_debug";

    private final SharedPreferences prefs;

    public ProfileStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ---------- profiles ---------- */

    public List<Profile> all() {
        List<Profile> out = new ArrayList<>();
        String raw = prefs.getString(K_PROFILES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Profile p = new Profile(o.optString("name", "Profile"));
                p.gamePackage = o.optString("pkg", "");
                p.sensitivity = o.optInt("sens", 50);
                p.aimSensitivity = o.optInt("aim", 50);
                p.pointerSpeed = o.optInt("ptr", 50);
                p.invertY = o.optBoolean("invY", false);
                p.buttonSize = (float) o.optDouble("btnSize", 1.0);
                p.buttonOpacity = (float) o.optDouble("btnOpacity", 0.85);
                p.orientation = o.optString("orient", "auto");
                p.joystick = o.optBoolean("joy", true);
                p.joyX = (float) o.optDouble("joyX", 0.16);
                p.joyY = (float) o.optDouble("joyY", 0.78);
                p.joyR = (float) o.optDouble("joyR", 0.11);
                p.aimEnabled = o.optBoolean("aim", true);
                p.aimZone = o.optString("aimZone", "right");
                JSONArray bs = o.optJSONArray("bindings");
                if (bs != null) {
                    for (int j = 0; j < bs.length(); j++) {
                        JSONObject b = bs.getJSONObject(j);
                        Binding bd = new Binding(b.optString("key", "A"),
                                (float) b.optDouble("x", 0.5),
                                (float) b.optDouble("y", 0.5),
                                b.optString("action", ""));
                        bd.size = (float) b.optDouble("size", 1.0);
                        p.bindings.add(bd);
                    }
                }
                out.add(p);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    public void save(List<Profile> profiles) {
        JSONArray arr = new JSONArray();
        for (Profile p : profiles) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", p.name);
                o.put("pkg", p.gamePackage);
                o.put("sens", p.sensitivity);
                o.put("aim", p.aimSensitivity);
                o.put("ptr", p.pointerSpeed);
                o.put("invY", p.invertY);
                o.put("btnSize", p.buttonSize);
                o.put("btnOpacity", p.buttonOpacity);
                o.put("orient", p.orientation);
                o.put("joy", p.joystick);
                o.put("joyX", p.joyX);
                o.put("joyY", p.joyY);
                o.put("joyR", p.joyR);
                o.put("aim", p.aimEnabled);
                o.put("aimZone", p.aimZone);
                JSONArray bs = new JSONArray();
                for (Binding b : p.bindings) {
                    JSONObject bo = new JSONObject();
                    bo.put("key", b.key);
                    bo.put("action", b.action == null ? "" : b.action);
                    bo.put("x", b.x);
                    bo.put("y", b.y);
                    bo.put("size", b.size);
                    bs.put(bo);
                }
                o.put("bindings", bs);
                arr.put(o);
            } catch (JSONException ignored) { }
        }
        prefs.edit().putString(K_PROFILES, arr.toString()).apply();
    }

    public void upsert(Profile profile) {
        List<Profile> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(profile.name)) { list.set(i, profile); save(list); return; }
        }
        list.add(profile);
        save(list);
    }

    public void delete(String name) {
        List<Profile> list = all();
        for (int i = list.size() - 1; i >= 0; i--)
            if (list.get(i).name.equalsIgnoreCase(name)) list.remove(i);
        save(list);
    }

    public Profile get(String name) {
        for (Profile p : all()) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    public void setActive(String name) { prefs.edit().putString(K_ACTIVE, name).apply(); }

    public Profile active() {
        String n = prefs.getString(K_ACTIVE, prefs.getString(K_DEFAULT, ""));
        if (n != null && !n.isEmpty()) {
            Profile p = get(n);
            if (p != null) return p;
        }
        List<Profile> all = all();
        return all.isEmpty() ? null : all.get(0);
    }

    /* ---------- settings ---------- */

    public String theme() { return prefs.getString(K_THEME, "dark"); }
    public void setTheme(String t) { prefs.edit().putString(K_THEME, t).apply(); }

    public boolean autoStart() { return prefs.getBoolean(K_AUTOSTART, false); }
    public void setAutoStart(boolean b) { prefs.edit().putBoolean(K_AUTOSTART, b).apply(); }

    public boolean inputDebug() { return prefs.getBoolean(K_DEBUG, false); }
    public void setInputDebug(boolean b) { prefs.edit().putBoolean(K_DEBUG, b).apply(); }

    public String defaultProfile() { return prefs.getString(K_DEFAULT, ""); }
    public void setDefaultProfile(String n) { prefs.edit().putString(K_DEFAULT, n).apply(); }

    public void resetAll() { prefs.edit().clear().apply(); }

    /* ---------- first run ---------- */

    public void ensureDefault() {
        if (!all().isEmpty()) return;
        Profile p = new Profile("Shooter (default)");
        p.bindings.add(new Binding("W", 0.18f, 0.70f, "Move Forward"));
        p.bindings.add(new Binding("A", 0.10f, 0.80f, "Move Left"));
        p.bindings.add(new Binding("S", 0.18f, 0.90f, "Move Back"));
        p.bindings.add(new Binding("D", 0.26f, 0.80f, "Move Right"));
        p.bindings.add(new Binding("SPACE", 0.86f, 0.88f, "Jump"));
        p.bindings.add(new Binding("R", 0.80f, 0.70f, "Reload"));
        p.bindings.add(new Binding("F", 0.72f, 0.80f, "Interact"));
        p.bindings.add(new Binding("SHIFT", 0.34f, 0.70f, "Sprint"));
        p.bindings.add(new Binding("MOUSE_LEFT", 0.90f, 0.55f, "Fire"));
        p.bindings.add(new Binding("MOUSE_RIGHT", 0.80f, 0.50f, "Aim"));
        upsert(p);
        setActive(p.name);
        setDefaultProfile(p.name);
    }
}
