package com.moke.aidemo;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Agnes AI OpenAI 兼容 API 客户端
 * 文档: https://apihub.agnes-ai.com/v1
 */
public class AgnesApiClient {

    private static final String API_URL = "https://apihub.agnes-ai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "agnes-2.0-flash";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    public interface Callback {
        void onSuccess(String reply);

        void onError(String error);
    }

    public void chat(Context context, String apiKey, String userMessage, Callback callback) {
        chat(context, apiKey, DEFAULT_MODEL, userMessage, callback);
    }

    public void chat(Context context, String apiKey, String model, String userMessage, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("temperature", 0.7);
                body.put("max_tokens", 1024);

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", buildSystemPrompt(context)));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", userMessage));
                body.put("messages", messages);

                connection = openConnection(apiKey);
                writeBody(connection, body.toString());

                int code = connection.getResponseCode();
                String responseText = readStream(connection, code >= 200 && code < 300);

                if (code < 200 || code >= 300) {
                    callback.onError(parseErrorMessage(responseText, code));
                    return;
                }

                callback.onSuccess(parseReply(responseText));
            } catch (Exception e) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "请求失败");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String buildSystemPrompt(Context context) {
        String now = ZonedDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()));
        String deviceInfo = DeviceInfoProvider.buildSummary(context);
        return "你是一个有帮助的 AI 助手。"
                + "用户设备的当前本地时间是：" + now + "。"
                + "回答与日期、时间、星期相关的问题时，请以上述时间为准。"
                + "\n\n以下为用户 Android 设备的基础信息（由客户端采集，可能因权限或系统限制部分字段不可用）：\n"
                + deviceInfo
                + "\n\n回答与设备相关的问题时请优先依据以上信息；不要编造未提供的标识符或位置。"
                + "涉及隐私的标识符（如 IMEI、位置）仅在用户明确询问时再说明。";
    }

    private HttpURLConnection openConnection(String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private void writeBody(HttpURLConnection connection, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }
    }

    private String readStream(HttpURLConnection connection, boolean success) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                success ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private String parseReply(String responseText) throws Exception {
        JSONObject root = new JSONObject(responseText);
        JSONArray choices = root.getJSONArray("choices");
        if (choices.length() == 0) {
            throw new Exception("响应中没有 choices");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message.getString("content");
    }

    private String parseErrorMessage(String responseText, int code) {
        try {
            JSONObject root = new JSONObject(responseText);
            if (root.has("error")) {
                JSONObject error = root.getJSONObject("error");
                if (error.has("message")) {
                    return "HTTP " + code + ": " + error.getString("message");
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (responseText == null || responseText.isEmpty()) {
            return "HTTP " + code;
        }
        return "HTTP " + code + ": " + responseText;
    }
}
