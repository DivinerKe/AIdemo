package com.moke.aidemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String[] DEVICE_INFO_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private TextView tvPermissionStatus;
    private TextView tvDeviceInfoPermission;
    private ChatController chatController;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> updatePermissionStatus()
            );

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    private final ActivityResultLauncher<String[]> deviceInfoPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (!hasAllDeviceInfoPermissions()) {
                            Toast.makeText(this, R.string.device_info_permission_denied, Toast.LENGTH_LONG).show();
                        }
                        updatePermissionStatus();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvDeviceInfoPermission = findViewById(R.id.tvDeviceInfoPermission);
        Button btnGrantOverlay = findViewById(R.id.btnGrantOverlay);
        Button btnGrantDeviceInfo = findViewById(R.id.btnGrantDeviceInfo);
        Button btnStartFloat = findViewById(R.id.btnStartFloat);
        Button btnStopFloat = findViewById(R.id.btnStopFloat);

        chatController = new ChatController();
        chatController.bind(findViewById(R.id.chatPanelRoot));

        btnGrantOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnGrantDeviceInfo.setOnClickListener(v -> requestDeviceInfoPermissions());
        btnStartFloat.setOnClickListener(v -> startFloatingOverlay());
        btnStopFloat.setOnClickListener(v -> stopFloatingOverlay());

        updatePermissionStatus();
        requestDeviceInfoPermissionsIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void requestOverlayPermission() {
        if (OverlayPermissionHelper.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        overlayPermissionLauncher.launch(OverlayPermissionHelper.buildSettingsIntent(this));
    }

    private void startFloatingOverlay() {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        requestNotificationPermissionIfNeeded();

        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
        Toast.makeText(this, R.string.float_started, Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void stopFloatingOverlay() {
        stopService(new Intent(this, FloatingOverlayService.class));
        Toast.makeText(this, R.string.float_stopped, Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void requestDeviceInfoPermissionsIfNeeded() {
        if (!hasAllDeviceInfoPermissions()) {
            requestDeviceInfoPermissions();
        }
    }

    private void requestDeviceInfoPermissions() {
        if (hasAllDeviceInfoPermissions()) {
            Toast.makeText(this, R.string.device_info_permission_granted, Toast.LENGTH_SHORT).show();
            updatePermissionStatus();
            return;
        }
        deviceInfoPermissionLauncher.launch(DEVICE_INFO_PERMISSIONS);
    }

    private boolean hasAllDeviceInfoPermissions() {
        for (String permission : DEVICE_INFO_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void updatePermissionStatus() {
        boolean granted = OverlayPermissionHelper.canDrawOverlays(this);
        tvPermissionStatus.setText(granted
                ? R.string.overlay_permission_granted
                : R.string.overlay_permission_hint);
        findViewById(R.id.btnStartFloat).setEnabled(granted);

        tvDeviceInfoPermission.setText(hasAllDeviceInfoPermissions()
                ? R.string.device_info_permission_granted
                : R.string.device_info_permission_hint);
    }
}
