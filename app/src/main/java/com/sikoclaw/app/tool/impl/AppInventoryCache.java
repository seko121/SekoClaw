package com.sikoclaw.app.tool.impl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent launchable-app inventory.  Phone tasks ask for this data often;
 * querying PackageManager each planning step is slow and gives the agent no
 * extra information.  A failed lookup refreshes once, so newly installed apps
 * remain discoverable without a stale-cache trap.
 */
public final class AppInventoryCache {
    private static final String PREFS = "octobot_app_inventory";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_AT = "saved_at";
    private static final long MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    public static final class Entry {
        public final String label;
        public final String packageName;
        Entry(String label, String packageName) { this.label = label; this.packageName = packageName; }
        public String row() { return label + " | " + packageName; }
    }

    private AppInventoryCache() { }

    public static synchronized List<Entry> apps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Entry> cached = parse(prefs.getString(KEY_ROWS, ""));
        if (!cached.isEmpty() && System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L) < MAX_AGE_MS) return cached;
        return refresh(context);
    }

    public static synchronized List<Entry> refresh(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);
        ArrayList<Entry> result = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String label = String.valueOf(info.loadLabel(pm));
            result.add(new Entry(label, info.activityInfo.packageName));
        }
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        StringBuilder raw = new StringBuilder();
        for (Entry entry : result) raw.append(entry.label.replace("\t", " ").replace("\n", " ")).append('\t').append(entry.packageName).append('\n');
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ROWS, raw.toString()).putLong(KEY_AT, System.currentTimeMillis()).apply();
        return result;
    }

    public static Entry find(Context context, String query) {
        Entry match = findIn(apps(context), query);
        return match != null ? match : findIn(refresh(context), query);
    }

    private static Entry findIn(List<Entry> entries, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) return null;
        Entry best = null; int bestScore = 0;
        for (Entry entry : entries) {
            String label = entry.label.toLowerCase(); String pkg = entry.packageName.toLowerCase();
            if (label.equals(normalized)) return entry;
            int score = label.contains(normalized) ? 20 + normalized.length() : pkg.contains(normalized.replace(" ", "")) ? 10 + normalized.length() : 0;
            if (score > bestScore) { bestScore = score; best = entry; }
        }
        return best;
    }

    private static List<Entry> parse(String raw) {
        ArrayList<Entry> items = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            int sep = line.indexOf('\t');
            if (sep > 0 && sep < line.length() - 1) items.add(new Entry(line.substring(0, sep), line.substring(sep + 1)));
        }
        return items;
    }
}
