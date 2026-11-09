package com.xcontroller.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** My Games — installed apps ki list, har game ke liye profile. */
public class GamesActivity extends Activity {

    private ProfileStore store;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new ProfileStore(this);
        build();
    }

    private void build() {
        LinearLayout box = UiKit.column(this);
        box.addView(UiKit.title(this, "My Games", 22));
        box.addView(UiKit.muted(this, "Game chunein — uska apna control profile banega"));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);

        Button add = UiKit.button(this, "+  Naya profile", UiKit.ACCENT, 0xFF04121A);
        add.setOnClickListener(v -> newProfile(null, ""));
        box.addView(add, UiKit.matchWrap(16));

        ScrollView sv = UiKit.scroll(this, box);
        setContentView(sv);
        loadProfiles();
        loadGames();
    }

    private void loadProfiles() {
        list.removeAllViews();
        List<ProfileStore.Profile> all = store.all();
        if (all.isEmpty()) {
            list.addView(UiKit.muted(this, "Abhi koi profile nahi"));
            return;
        }
        ProfileStore.Profile active = store.active();
        for (final ProfileStore.Profile p : all) {
            LinearLayout c = UiKit.card(this);
            TextView t = UiKit.title(this, p.name
                    + (active != null && active.name.equals(p.name) ? "   ● ACTIVE" : ""), 16);
            c.addView(t);
            c.addView(UiKit.muted(this, p.bindings.size() + " controls · sens " + p.sensitivity
                    + "%" + (p.gamePackage.isEmpty() ? "" : " · " + p.gamePackage)));

            LinearLayout row = UiKit.row(this);
            row.setPadding(0, 16, 0, 0);
            Button edit = UiKit.button(this, "Mapping", 0xFF1B2636, UiKit.TEXT);
            edit.setLayoutParams(UiKit.weight(1f));
            edit.setOnClickListener(v -> {
                store.setActive(p.name);
                startActivity(new Intent(this, MappingActivity.class).putExtra("profile", p.name));
            });
            row.addView(edit);
            row.addView(UiKit.spacer(this, 14, 1));
            Button use = UiKit.button(this, "Use", 0xFF123542, UiKit.ACCENT);
            use.setLayoutParams(UiKit.weight(1f));
            use.setOnClickListener(v -> { store.setActive(p.name); Toast.makeText(this, p.name + " active", Toast.LENGTH_SHORT).show(); loadProfiles(); });
            row.addView(use);
            row.addView(UiKit.spacer(this, 14, 1));
            Button del = UiKit.button(this, "🗑", 0xFF3A1E22, UiKit.RED);
            del.setLayoutParams(UiKit.weight(0.6f));
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete profile?")
                    .setMessage(p.name)
                    .setPositiveButton("Delete", (d, w) -> { store.delete(p.name); loadProfiles(); })
                    .setNegativeButton("Cancel", null).show());
            row.addView(del);
            c.addView(row);
            list.addView(c);
        }
    }

    private void loadGames() {
        TextView head = UiKit.title(this, "Installed games", 16);
        head.setPadding(0, 22, 0, 10);
        list.addView(head);

        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        List<ResolveInfo> games = new ArrayList<>();
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            if (pkg.contains("game") || label.toLowerCase().contains("game")
                    || label.toLowerCase().contains("fire") || label.toLowerCase().contains("bgmi")
                    || label.toLowerCase().contains("clash") || label.toLowerCase().contains("racing")) {
                games.add(ri);
            }
        }
        Collections.sort(games, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        if (games.isEmpty()) {
            list.addView(UiKit.muted(this, "Koi game detect nahi hua — niche se manual profile banayein"));
            return;
        }
        for (final ResolveInfo ri : games) {
            final String pkg = ri.activityInfo.packageName;
            final String label = String.valueOf(ri.loadLabel(pm));
            LinearLayout row = UiKit.card(this);
            row.addView(UiKit.title(this, label, 15));
            row.addView(UiKit.muted(this, pkg));
            Button b = UiKit.button(this, "Profile banayein", 0xFF123542, UiKit.ACCENT);
            b.setOnClickListener(v -> newProfile(label, pkg));
            row.addView(b, UiKit.matchWrap(14));
            list.addView(row);
        }
    }

    private void newProfile(String label, String pkg) {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint("Profile ka naam");
        if (label != null) et.setText(label);
        new AlertDialog.Builder(this)
                .setTitle("Naya profile")
                .setView(et)
                .setPositiveButton("Banao", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "Profile " + (store.all().size() + 1);
                    ProfileStore.Profile p = new ProfileStore.Profile(name);
                    p.gamePackage = pkg == null ? "" : pkg;
                    store.upsert(p);
                    store.setActive(name);
                    startActivity(new Intent(this, MappingActivity.class).putExtra("profile", name));
                    finish();
                })
                .setNegativeButton("Cancel", null).show();
    }
}
