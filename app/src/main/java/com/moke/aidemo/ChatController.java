package com.moke.aidemo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全屏 / 悬浮窗共用的对话逻辑
 */
public class ChatController {

    private final AgnesApiClient agnesApiClient = new AgnesApiClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText etApiKey;
    private EditText etPrompt;
    private TextView tvStatus;
    private TextView tvResponse;
    private ScrollView scrollResponse;
    private Button btnSend;
    private Context context;

    public void bind(View root) {
        context = root.getContext();
        etApiKey = root.findViewById(R.id.etApiKey);
        etPrompt = root.findViewById(R.id.etPrompt);
        tvStatus = root.findViewById(R.id.tvStatus);
        tvResponse = root.findViewById(R.id.tvResponse);
        scrollResponse = root.findViewById(R.id.scrollResponse);
        btnSend = root.findViewById(R.id.btnSend);

        if (!BuildConfig.AGNES_API_KEY.isEmpty()) {
            etApiKey.setText(BuildConfig.AGNES_API_KEY);
            etApiKey.setVisibility(View.GONE);
            tvStatus.setText(R.string.status_key_loaded);
        }

        btnSend.setOnClickListener(v -> sendPrompt());
    }

    private void sendPrompt() {
        String apiKey = etApiKey.getText().toString().trim();
        String prompt = etPrompt.getText().toString().trim();

        if (apiKey.isEmpty()) {
            toast(R.string.error_api_key_empty);
            return;
        }
        if (prompt.isEmpty()) {
            toast(R.string.error_prompt_empty);
            return;
        }

        setLoading(true);
        tvResponse.setText("");

        agnesApiClient.chat(context.getApplicationContext(), apiKey, prompt, new AgnesApiClient.Callback() {
            @Override
            public void onSuccess(String reply) {
                mainHandler.post(() -> {
                    setLoading(false);
                    tvResponse.setText(reply);
                    scrollResponse.post(() -> scrollResponse.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    setLoading(false);
                    tvStatus.setText(context.getString(R.string.status_error, error));
                    tvResponse.setText(error);
                    toast(error);
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        btnSend.setEnabled(!loading);
        tvStatus.setText(loading ? R.string.status_requesting : R.string.status_done);
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private void toast(int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
    }
}
