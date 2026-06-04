package com.moke.aidemo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本机可启动应用列表，供 AI 选择包名并在本地校验。
 */
public final class InstalledAppsCatalog {

    private static final int MAX_APPS_IN_PROMPT = 100;

    private static volatile Snapshot cached;

    private InstalledAppsCatalog() {
    }

    public static Snapshot load(Context context) {
        Snapshot snap = cached;
        if (snap != null) {
            return snap;
        }
        synchronized (InstalledAppsCatalog.class) {
            if (cached != null) {
                return cached;
            }
            cached = buildSnapshot(context.getApplicationContext());
            return cached;
        }
    }

    public static void invalidate() {
        cached = null;
    }

    /** 用户说「打开微信」时本地快速匹配（不经过模型） */
    public static String matchPackageForOpenPhrase(Context context, String userMessage) {
        if (userMessage == null) {
            return null;
        }
        String trimmed = userMessage.trim();
        String[] prefixes = {"打开", "启动", "运行", "open ", "launch "};
        String target = null;
        for (String prefix : prefixes) {
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())
                    && trimmed.length() > prefix.length()) {
                target = trimmed.substring(prefix.length()).trim();
                break;
            }
        }
        if (TextUtils.isEmpty(target)) {
            return null;
        }
        target = target.replaceAll("[。！？!?\\s]+$", "");
        return load(context).findPackageByLabel(target);
    }

    private static Snapshot buildSnapshot(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(launcher, 0);
        Map<String, String> packageToLabel = new HashMap<>();
        for (ResolveInfo info : activities) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs != null ? labelCs.toString().trim() : pkg;
            if (!packageToLabel.containsKey(pkg) || label.length() < packageToLabel.get(pkg).length()) {
                packageToLabel.put(pkg, label);
            }
        }

        List<AppEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : packageToLabel.entrySet()) {
            entries.add(new AppEntry(e.getValue(), e.getKey()));
        }
        Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));

        return new Snapshot(entries);
    }

    public static final class AppEntry {
        public final String label;
        public final String packageName;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    public static final class Snapshot {
        private final List<AppEntry> entries;
        private final Map<String, String> labelToPackage;

        Snapshot(List<AppEntry> entries) {
            this.entries = Collections.unmodifiableList(entries);
            labelToPackage = new HashMap<>();
            for (AppEntry e : entries) {
                labelToPackage.put(normalize(e.label), e.packageName);
            }
        }

        public boolean isLaunchable(String packageName) {
            if (packageName == null) {
                return false;
            }
            for (AppEntry e : entries) {
                if (e.packageName.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }

        public String findPackageByLabel(String label) {
            if (TextUtils.isEmpty(label)) {
                return null;
            }
            String key = normalize(label);
            if (labelToPackage.containsKey(key)) {
                return labelToPackage.get(key);
            }
            String bestPkg = null;
            int bestScore = 0;
            for (AppEntry e : entries) {
                String nLabel = normalize(e.label);
                if (nLabel.contains(key) || key.contains(nLabel)) {
                    int score = Math.min(nLabel.length(), key.length());
                    if (score > bestScore) {
                        bestScore = score;
                        bestPkg = e.packageName;
                    }
                }
            }
            return bestPkg;
        }

        public String labelForPackage(String packageName) {
            for (AppEntry e : entries) {
                if (e.packageName.equals(packageName)) {
                    return e.label;
                }
            }
            return packageName;
        }

        public String formatForSystemPrompt() {
            int total = entries.size();
            int limit = Math.min(total, MAX_APPS_IN_PROMPT);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                AppEntry e = entries.get(i);
                sb.append(e.label).append('|').append(e.packageName).append('\n');
            }
            if (total > limit) {
                sb.append("… 另有 ").append(total - limit).append(" 个应用未列出，请让用户说明更具体的名称。");
            }
            return sb.toString();
        }
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
