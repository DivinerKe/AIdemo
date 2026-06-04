package com.moke.aidemo;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONObject;

/**
 * 执行 AI 下发的白名单动作（打开应用 / 系统设置页）。
 */
public final class AgentActionExecutor {

    public static final class Result {
        public final boolean success;
        public final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private AgentActionExecutor() {
    }

    public static Result execute(Context context, JSONObject action) {
        if (action == null) {
            return new Result(false, "无动作");
        }
        String type = action.optString("action", "");
        if (TextUtils.isEmpty(type)) {
            return new Result(false, "缺少 action 字段");
        }

        Context app = context.getApplicationContext();
        InstalledAppsCatalog.Snapshot apps = InstalledAppsCatalog.load(app);

        switch (type) {
            case "open_app":
                return openApp(app, apps, action.optString("package", ""));
            case "open_settings":
                return startActivity(app, new Intent(Settings.ACTION_SETTINGS));
            case "open_wifi_settings":
                return startActivity(app, new Intent(Settings.ACTION_WIFI_SETTINGS));
            default:
                return new Result(false, "不支持的动作: " + type);
        }
    }

    public static Result openApp(Context context, InstalledAppsCatalog.Snapshot apps, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return new Result(false, "缺少应用包名");
        }
        if (!apps.isLaunchable(packageName)) {
            return new Result(false, "未安装或无法启动: " + packageName);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            return new Result(false, "无法获取启动入口: " + packageName);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startActivity(context, launch);
    }

    private static Result startActivity(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, "已执行");
        } catch (ActivityNotFoundException e) {
            return new Result(false, "系统无法打开该页面");
        } catch (Exception e) {
            String msg = e.getMessage();
            return new Result(false, msg != null ? msg : "执行失败");
        }
    }
}
