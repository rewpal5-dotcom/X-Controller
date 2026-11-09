package com.xcontroller.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ProfileStore — har game ke control profile (key -> screen point) save karta hai.
 * SharedPreferences mein JSON ke roop mein rakha jaata hai. Koi database nahi chahiye.
 */
public class ProfileStore {

    public static class Binding {
        public String key;      // jaise "W", "SPACE", "MOUSE_LEFT"
        public float x, y;      // 0.0 - 1.0 (screen ka hissa)
        public Binding(String key, float x, float y) { this.key = key; this.x = x; this.y = y; }
    }

    public static class Profile {
        public String name;
        public List<Binding> bindings = new ArrayList<>();
        public int sensitivity = 50;

        public Profile(String name) { this.name = name; }

        public Binding find(String key) {
            for (Binding b : bindings) if (b.key.equalsIgnoreCase(key)) return b;
            return null;
        }
    }

    private static final String PREFS = "xcontroller";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active";

    private final SharedPreferences prefs;

    public ProfileStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Profile> all() {
        List<Profile> out = new ArrayList<>();
        String raw = prefs.getString(KEY_PROFILES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Profile p = new Profile(o.optString("name", "Profile"));
                p.sensitivity = o.optInt("sensitivity", 50);
                JSONArray bs = o.optJSONArray("bindings");
                if (bs != null) {
                    for (int j = 0; j < bs.length(); j++) {
                        JSONObject b = bs.getJSONObject(j);
                        p.bindings.add(new Binding(b.optString("key", "A"),
                                (float) b.optDouble("x", 0.5), (float) b.optDouble("y", 0.5)));
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
                o.put("sensitivity", p.sensitivity);
                JSONArray bs = new JSONArray();
                for (Binding b : p.bindings) {
                    JSONObject bo = new JSONObject();
                    bo.put("key", b.key);
                    bo.put("x", b.x);
                    bo.put("y", b.y);
                    bs.put(bo);
                }
                o.put("bindings", bs);
                arr.put(o);
            } catch (JSONException ignored) { }
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply();
    }

    public void upsert(Profile profile) {
        List<Profile> list = all();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(profile.name)) { list.set(i, profile); found = true; break; }
        }
        if (!found) list.add(profile);
        save(list);
    }

    public void delete(String name) {
        List<Profile> list = all();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equalsIgnoreCase(name)) list.remove(i);
        }
        save(list);
    }

    public void setActive(String name) { prefs.edit().putString(KEY_ACTIVE, name).apply(); }

    public Profile active() {
        String name = prefs.getString(KEY_ACTIVE, "");
        if (name == null || name.isEmpty()) {
            List<Profile> all = all();
            return all.isEmpty() ? null : all.get(0);
        }
        for (Profile p : all()) if (p.name.equalsIgnoreCase(name)) return p;
        List<Profile> all = all();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Pehli baar app khulne par kaam ka default profile bana do. */
    public void ensureDefault() {
        if (!all().isEmpty()) return;
        Profile p = new Profile("Shooter (default)");
        p.bindings.add(new Binding("W", 0.18f, 0.72f));
        p.bindings.add(new Binding("A", 0.10f, 0.82f));
        p.bindings.add(new Binding("S", 0.18f, 0.90f));
        p.bindings.add(new Binding("D", 0.26f, 0.82f));
        p.bindings.add(new Binding("SPACE", 0.86f, 0.88f));
        p.bindings.add(new Binding("R", 0.80f, 0.70f));
        p.bindings.add(new Binding("F", 0.72f, 0.80f));
        p.bindings.add(new Binding("MOUSE_LEFT", 0.90f, 0.55f));
        upsert(p);
        setActive(p.name);
    }
}
