package com.moke.aidemo;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

/**
 * 系统悬浮窗：海豚悬浮球 + 对话面板
 */
public class FloatingOverlayService extends Service {

    private static final String TAG = "FloatingOverlay";

    public static final String ACTION_START = "com.moke.aidemo.action.START_FLOAT";
    public static final String ACTION_STOP = "com.moke.aidemo.action.STOP_FLOAT";

    private static final String CHANNEL_ID = "float_overlay";
    private static final int NOTIFICATION_ID = 1;
    private static final int DRAG_THRESHOLD_PX = 20;

    private static final int BUBBLE_SIZE_DP = 80;
    private static final int PANEL_WIDTH_DP = 360;
    private static final int PANEL_HEIGHT_DP = 520;
    private static final float BUBBLE_ALPHA_IDLE = 0.55f;
    private static final float BUBBLE_ALPHA_ACTIVE = 1.0f;
    private static final long BUBBLE_ALPHA_ANIM_MS = 150L;

    /** 无操作多久后开始游动（毫秒） */
    private static final long IDLE_BEFORE_SWIM_MS = 5000L;
    private static final int SWIM_RANGE_X_DP = 32;
    private static final int SWIM_RANGE_Y_DP = 14;
    private static final long SWIM_CYCLE_MS = 4800L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable startSwimRunnable = this::tryStartSwimAnimation;

    private WindowManager windowManager;
    private LayoutInflater themedInflater;
    private View bubbleView;
    private View panelView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;

    private ChatController chatController;
    private BroadcastReceiver screenReceiver;

    private ValueAnimator swimAnimator;
    private int swimHomeX;
    private int swimHomeY;
    /** 上次悬浮球位置（关闭面板后恢复，避免回到初始左上角） */
    private int bubbleLastX = Integer.MIN_VALUE;
    private int bubbleLastY = Integer.MIN_VALUE;
    private boolean isScreenOn = true;
    private boolean isSwimAnimating = false;
    private boolean bubbleTouchActive = false;
    private boolean bubbleGestureOnDolphin = false;
    private Bitmap dolphinHitBitmap;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Context themedContext = new ContextThemeWrapper(getApplicationContext(), R.style.Theme_AIdemo);
        themedInflater = LayoutInflater.from(themedContext);
        registerScreenReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.e(TAG, "overlay permission missing");
            showToast(getString(R.string.overlay_permission_required));
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
        showBubble();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSwimAnimation();
        unregisterScreenReceiver();
        removeAllViews();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.float_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.float_notification_title))
                .setContentText(getString(R.string.float_notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    private void showBubble() {
        if (bubbleView != null) {
            return;
        }

        try {
            bubbleView = themedInflater.inflate(R.layout.float_bubble, null);
            int size = dp(BUBBLE_SIZE_DP);
            bubbleParams = createBubbleLayoutParams(size);
            bubbleParams.gravity = Gravity.TOP | Gravity.START;
            if (bubbleLastX == Integer.MIN_VALUE) {
                bubbleParams.x = dp(16);
                bubbleParams.y = dp(120);
                bubbleLastX = bubbleParams.x;
                bubbleLastY = bubbleParams.y;
            } else {
                bubbleParams.x = bubbleLastX;
                bubbleParams.y = bubbleLastY;
            }
            updateSwimHomePosition();

            attachBubbleTouchListener();

            windowManager.addView(bubbleView, bubbleParams);
            setBubbleAlpha(BUBBLE_ALPHA_IDLE, false);
            scheduleSwimIfIdle();
            Log.i(TAG, "bubble shown " + size + "x" + size + " at " + bubbleParams.x + "," + bubbleParams.y);
        } catch (Exception e) {
            Log.e(TAG, "showBubble failed", e);
            showToast(getString(R.string.float_add_failed, e.getMessage()));
        }
    }

    private void showPanel() {
        if (panelView != null) {
            return;
        }

        stopSwimAnimation();
        Log.i(TAG, "showPanel");

        try {
            panelView = themedInflater.inflate(R.layout.float_chat_panel, null);
            int panelW = dp(PANEL_WIDTH_DP);
            int panelH = dp(PANEL_HEIGHT_DP);
            panelParams = createPanelLayoutParams(panelW, panelH);
            panelParams.gravity = Gravity.TOP | Gravity.START;
            panelParams.x = bubbleParams != null ? bubbleParams.x : dp(16);
            panelParams.y = bubbleParams != null ? bubbleParams.y : dp(80);

            View dragHandle = panelView.findViewById(R.id.layoutDragHandle);
            attachPanelDragListener(dragHandle);

            panelView.findViewById(R.id.btnFloatClose).setOnClickListener(v -> hidePanel());

            chatController = new ChatController();
            chatController.bind(panelView);

            windowManager.addView(panelView, panelParams);
            Log.i(TAG, "panel shown " + panelW + "x" + panelH);

            hideBubble();

            panelView.post(() -> {
                View prompt = panelView.findViewById(R.id.etPrompt);
                if (prompt != null) {
                    prompt.requestFocus();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "showPanel failed", e);
            showToast(getString(R.string.float_add_failed, e.getMessage()));
            if (panelView != null) {
                try {
                    windowManager.removeView(panelView);
                } catch (Exception ignored) {
                }
                panelView = null;
                panelParams = null;
                chatController = null;
            }
            if (bubbleView == null) {
                showBubble();
            }
        }
    }

    private void hidePanel() {
        if (panelView != null) {
            try {
                windowManager.removeView(panelView);
            } catch (Exception e) {
                Log.w(TAG, "hidePanel remove failed", e);
            }
            panelView = null;
            panelParams = null;
            chatController = null;
        }
        showBubble();
    }

    private void hideBubble() {
        stopSwimAnimation();
        if (bubbleView != null) {
            if (bubbleParams != null) {
                bubbleLastX = bubbleParams.x;
                bubbleLastY = bubbleParams.y;
            }
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception e) {
                Log.w(TAG, "hideBubble remove failed", e);
            }
            bubbleView = null;
            bubbleParams = null;
        }
    }

    private void removeAllViews() {
        if (panelView != null) {
            try {
                windowManager.removeView(panelView);
            } catch (Exception ignored) {
            }
            panelView = null;
            panelParams = null;
            chatController = null;
        }
        hideBubble();
    }

    private void attachBubbleTouchListener() {
        ensureDolphinHitBitmap();
        bubbleView.setClickable(false);
        bubbleView.setFocusable(false);

        bubbleView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (!isTouchOnDolphin(v, event)) {
                        return false;
                    }
                    bubbleGestureOnDolphin = true;
                    onBubbleTouchStart();
                    setBubbleAlpha(BUBBLE_ALPHA_ACTIVE, true);
                    v.setTag(new float[]{
                            event.getRawX(), event.getRawY(),
                            bubbleParams.x, bubbleParams.y, 0f
                    });
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    if (!bubbleGestureOnDolphin) {
                        return false;
                    }
                    Object tag = v.getTag();
                    if (!(tag instanceof float[])) {
                        return true;
                    }
                    float[] state = (float[]) tag;
                    if (state.length < 5) {
                        return true;
                    }
                    float dx = event.getRawX() - state[0];
                    float dy = event.getRawY() - state[1];
                    if (state[4] == 0f) {
                        if (Math.abs(dx) > DRAG_THRESHOLD_PX || Math.abs(dy) > DRAG_THRESHOLD_PX) {
                            state[4] = 1f;
                            v.setTag(state);
                        }
                        return true;
                    }
                    bubbleParams.x = (int) (state[2] + dx);
                    bubbleParams.y = (int) (state[3] + dy);
                    bubbleLastX = bubbleParams.x;
                    bubbleLastY = bubbleParams.y;
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                    return true;
                }

                case MotionEvent.ACTION_UP: {
                    if (!bubbleGestureOnDolphin) {
                        return false;
                    }
                    bubbleGestureOnDolphin = false;
                    Object tag = v.getTag();
                    boolean dragged = false;
                    if (tag instanceof float[]) {
                        float[] state = (float[]) tag;
                        dragged = state.length >= 5 && state[4] == 1f;
                    }
                    v.setTag(null);
                    updateSwimHomePosition();
                    if (!dragged) {
                        bubbleTouchActive = false;
                        Log.i(TAG, "bubble tap");
                        setBubbleAlpha(BUBBLE_ALPHA_ACTIVE, false);
                        mainHandler.post(this::showPanel);
                    } else {
                        setBubbleAlpha(BUBBLE_ALPHA_IDLE, true);
                        onBubbleTouchEnd();
                    }
                    return true;
                }

                case MotionEvent.ACTION_CANCEL:
                    bubbleGestureOnDolphin = false;
                    v.setTag(null);
                    setBubbleAlpha(BUBBLE_ALPHA_IDLE, true);
                    updateSwimHomePosition();
                    onBubbleTouchEnd();
                    return true;

                default:
                    return false;
            }
        });
    }

    private void attachPanelDragListener(View dragHandle) {
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setTag(new float[]{
                            event.getRawX(), event.getRawY(),
                            panelParams.x, panelParams.y, 0f
                    });
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    Object tag = v.getTag();
                    if (!(tag instanceof float[])) {
                        return true;
                    }
                    float[] state = (float[]) tag;
                    if (state.length < 5) {
                        return true;
                    }
                    float dx = event.getRawX() - state[0];
                    float dy = event.getRawY() - state[1];
                    if (state[4] == 0f) {
                        if (Math.abs(dx) > DRAG_THRESHOLD_PX || Math.abs(dy) > DRAG_THRESHOLD_PX) {
                            state[4] = 1f;
                            v.setTag(state);
                        }
                        return true;
                    }
                    panelParams.x = (int) (state[2] + dx);
                    panelParams.y = (int) (state[3] + dy);
                    windowManager.updateViewLayout(panelView, panelParams);
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setTag(null);
                    return true;

                default:
                    return false;
            }
        });
    }

    private void registerScreenReceiver() {
        if (screenReceiver != null) {
            return;
        }
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isScreenOn = false;
                    stopSwimAnimation();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)
                        || Intent.ACTION_USER_PRESENT.equals(action)) {
                    isScreenOn = true;
                    scheduleSwimIfIdle();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
    }

    private void unregisterScreenReceiver() {
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
            screenReceiver = null;
        }
    }

    private void onBubbleTouchStart() {
        bubbleTouchActive = true;
        stopSwimAnimation();
    }

    private void onBubbleTouchEnd() {
        bubbleTouchActive = false;
        scheduleSwimIfIdle();
    }

    private void scheduleSwimIfIdle() {
        mainHandler.removeCallbacks(startSwimRunnable);
        if (bubbleView == null || panelView != null || !isScreenOn || bubbleTouchActive) {
            return;
        }
        mainHandler.postDelayed(startSwimRunnable, IDLE_BEFORE_SWIM_MS);
    }

    private void tryStartSwimAnimation() {
        if (bubbleView == null || bubbleParams == null || panelView != null
                || !isScreenOn || bubbleTouchActive || isSwimAnimating) {
            return;
        }
        startSwimAnimation();
    }

    private void startSwimAnimation() {
        if (bubbleView == null || bubbleParams == null || isSwimAnimating) {
            return;
        }
        updateSwimHomePosition();
        isSwimAnimating = true;
        setBubbleAlpha(BUBBLE_ALPHA_IDLE, true);

        final int rangeX = dp(SWIM_RANGE_X_DP);
        final int rangeY = dp(SWIM_RANGE_Y_DP);

        swimAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        swimAnimator.setDuration(SWIM_CYCLE_MS);
        swimAnimator.setRepeatCount(ValueAnimator.INFINITE);
        swimAnimator.setInterpolator(new LinearInterpolator());
        swimAnimator.addUpdateListener(animation -> {
            if (bubbleView == null || bubbleParams == null || !isSwimAnimating) {
                return;
            }
            float t = (float) animation.getAnimatedValue();
            float sinT = (float) Math.sin(t);
            float cosT = (float) Math.cos(t);
            bubbleParams.x = swimHomeX + (int) (sinT * rangeX);
            bubbleParams.y = swimHomeY + (int) (sinT * 0.65f * rangeY + cosT * rangeY * 0.35f);
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
                bubbleView.setRotation(cosT * 12f);
            } catch (Exception e) {
                Log.w(TAG, "swim update failed", e);
                stopSwimAnimation();
            }
        });
        swimAnimator.start();
        Log.i(TAG, "swim animation started");
    }

    private void stopSwimAnimation() {
        mainHandler.removeCallbacks(startSwimRunnable);
        if (swimAnimator != null) {
            swimAnimator.cancel();
            swimAnimator = null;
        }
        if (bubbleView != null) {
            bubbleView.setRotation(0f);
        }
        if (bubbleParams != null) {
            updateSwimHomePosition();
        }
        isSwimAnimating = false;
    }

    private void updateSwimHomePosition() {
        if (bubbleParams != null) {
            swimHomeX = bubbleParams.x;
            swimHomeY = bubbleParams.y;
        }
    }

    private void ensureDolphinHitBitmap() {
        if (dolphinHitBitmap == null) {
            dolphinHitBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dolphin_mascot);
        }
    }

    /** 仅当触点落在海豚非透明像素上时才响应 */
    private boolean isTouchOnDolphin(View root, MotionEvent event) {
        ImageView iv = root.findViewById(R.id.ivBubble);
        if (iv == null || iv.getWidth() <= 0 || iv.getHeight() <= 0) {
            return false;
        }
        ensureDolphinHitBitmap();
        if (dolphinHitBitmap == null) {
            return false;
        }

        float localX = event.getX() - iv.getLeft();
        float localY = event.getY() - iv.getTop();
        if (localX < 0 || localY < 0 || localX >= iv.getWidth() || localY >= iv.getHeight()) {
            return false;
        }

        float scaleX = dolphinHitBitmap.getWidth() / (float) iv.getWidth();
        float scaleY = dolphinHitBitmap.getHeight() / (float) iv.getHeight();
        int px = (int) (localX * scaleX);
        int py = (int) (localY * scaleY);
        px = Math.max(0, Math.min(px, dolphinHitBitmap.getWidth() - 1));
        py = Math.max(0, Math.min(py, dolphinHitBitmap.getHeight() - 1));
        int alpha = (dolphinHitBitmap.getPixel(px, py) >> 24) & 0xFF;
        return alpha > 48;
    }

    private WindowManager.LayoutParams createBubbleLayoutParams(int sizePx) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    private WindowManager.LayoutParams createPanelLayoutParams(int widthPx, int heightPx) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                widthPx,
                heightPx,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    private void setBubbleAlpha(float alpha, boolean animate) {
        if (bubbleView == null) {
            return;
        }
        bubbleView.animate().cancel();
        if (animate) {
            bubbleView.animate()
                    .alpha(alpha)
                    .setDuration(BUBBLE_ALPHA_ANIM_MS)
                    .start();
        } else {
            bubbleView.setAlpha(alpha);
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
