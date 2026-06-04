package com.moke.aidemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * 采集设备基础信息，供 AI system 提示使用（会随请求发往云端）。
 */
public final class DeviceInfoProvider {

    private DeviceInfoProvider() {
    }

    public static String buildSummary(Context context) {
        Context app = context.getApplicationContext();
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "品牌", Build.BRAND);
        appendLine(sb, "厂商", Build.MANUFACTURER);
        appendLine(sb, "机型", Build.MODEL);
        appendLine(sb, "设备代号", Build.DEVICE);
        appendLine(sb, "Android 版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        appendLine(sb, "系统构建号", Build.DISPLAY);
        appendLine(sb, "屏幕分辨率", readScreenResolution(app));
        appendLine(sb, "屏幕密度", readScreenDensity(app));
        appendBattery(sb, app);
        appendLine(sb, "网络类型", readNetworkType(app));
        appendLine(sb, "IMEI", readImei(app));
        appendLine(sb, "Android ID", readAndroidId(app));
        appendLine(sb, "位置", readLocation(app));
        appendLine(sb, "语言/地区", Locale.getDefault().toString());
        appendLine(sb, "应用包名", app.getPackageName());
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        sb.append(label).append("：").append(value != null && !value.isEmpty() ? value : "未知").append('\n');
    }

    private static String readScreenResolution(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null && wm.getDefaultDisplay() != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        } else {
            dm = context.getResources().getDisplayMetrics();
        }
        return dm.widthPixels + " x " + dm.heightPixels + " px";
    }

    private static String readScreenDensity(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return String.format(Locale.US, "%.2f dp/inch (%d dpi)", dm.density, dm.densityDpi);
    }

    private static void appendBattery(StringBuilder sb, Context context) {
        int level = -1;
        boolean charging = false;
        String statusLabel = "未知";

        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            statusLabel = batteryStatusLabel(status);
        }

        if (level < 0 || level > 100) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = context.registerReceiver(null, filter);
            if (battery != null) {
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int raw = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                if (scale > 0 && raw >= 0) {
                    level = raw * 100 / scale;
                }
                int st = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING
                        || st == BatteryManager.BATTERY_STATUS_FULL;
                statusLabel = batteryStatusLabel(st);
            }
        }

        String levelText = (level >= 0 && level <= 100) ? level + "%" : "未知";
        appendLine(sb, "电量", levelText + (charging ? "（充电中）" : "（未充电）") + "，状态：" + statusLabel);
    }

    private static String batteryStatusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            default:
                return "未知";
        }
    }

    private static String readNetworkType(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return "未知";
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return "无网络连接";
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) {
            return "未知";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "Wi-Fi";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "移动数据";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "以太网";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return "VPN";
        }
        return "其他";
    }

    private static String readImei(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return "未授权 READ_PHONE_STATE";
        }
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            return "不可用";
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String imei0 = tm.getImei(0);
                int simCount = simSlotCount(tm);
                if (imei0 != null && !imei0.isEmpty() && simCount <= 1) {
                    return imei0;
                }
                if (simCount > 1) {
                    String imei1 = tm.getImei(1);
                    if ((imei0 != null && !imei0.isEmpty()) || (imei1 != null && !imei1.isEmpty())) {
                        return "卡1: " + nullToDash(imei0) + "，卡2: " + nullToDash(imei1);
                    }
                } else if (imei0 != null && !imei0.isEmpty()) {
                    return imei0;
                }
            } else {
                @SuppressWarnings("deprecation")
                String id = tm.getDeviceId();
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            }
        } catch (SecurityException e) {
            return "系统拒绝访问（Android 10+ 对普通应用常不可用）";
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
        return "不可用（系统限制，非系统应用通常无法读取 IMEI）";
    }

    private static String readAndroidId(Context context) {
        try {
            String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return id != null ? id : "未知";
        } catch (Exception e) {
            return "读取失败";
        }
    }

    private static String readLocation(Context context) {
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            return "未授权位置权限";
        }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return "不可用";
        }

        Location best = null;
        if (fine) {
            best = pickNewer(best, safeLastKnown(lm, LocationManager.GPS_PROVIDER));
        }
        best = pickNewer(best, safeLastKnown(lm, LocationManager.NETWORK_PROVIDER));
        if (fine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            best = pickNewer(best, safeLastKnown(lm, LocationManager.FUSED_PROVIDER));
        }

        if (best == null) {
            return "暂无缓存位置（请先在系统地图/天气等应用中获取一次定位）";
        }
        return String.format(Locale.US,
                "纬度 %.6f，经度 %.6f，精度约 %.0f 米，时间 %tF %<tT",
                best.getLatitude(), best.getLongitude(), best.getAccuracy(), best.getTime());
    }

    private static Location safeLastKnown(LocationManager lm, String provider) {
        try {
            if (!lm.isProviderEnabled(provider)) {
                return null;
            }
            return lm.getLastKnownLocation(provider);
        } catch (SecurityException e) {
            return null;
        }
    }

    private static int simSlotCount(TelephonyManager tm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return tm.getActiveModemCount();
        }
        return tm.getPhoneCount();
    }

    private static String nullToDash(String value) {
        return value != null && !value.isEmpty() ? value : "-";
    }

    private static Location pickNewer(Location a, Location b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.getTime() >= b.getTime() ? a : b;
    }
}
