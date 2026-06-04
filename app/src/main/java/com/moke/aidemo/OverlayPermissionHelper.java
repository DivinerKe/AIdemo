package com.moke.aidemo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public final class OverlayPermissionHelper {

    private OverlayPermissionHelper() {
    }

    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static Intent buildSettingsIntent(Context context) {
        return new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName())
        );
    }
}
