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
        String appList = InstalledAppsCatalog.load(context).formatForSystemPrompt();
        return "你是一个有帮助的 AI 助手，运行在用户手机上的 Agnes 悬浮助手 App 中。"
                + "用户设备的当前本地时间是：" + now + "。"
                + "回答与日期、时间、星期相关的问题时，请以上述时间为准。"
                + "\n\n以下为用户 Android 设备的基础信息（由客户端采集，可能因权限或系统限制部分字段不可用）：\n"
                + deviceInfo
                + "\n\n回答与设备相关的问题时请优先依据以上信息；不要编造未提供的标识符或位置。"
                + "涉及隐私的标识符（如 IMEI、位置）仅在用户明确询问时再说明。"
                + "\n\n【打开应用】当用户明确要求打开某个已安装应用或系统设置页时："
                + "先用一两句中文简短说明，然后在回复末尾单独追加一行指令（用户看不到该标签内的 JSON 会被客户端解析执行）："
                + "\n<agent_action>{\"action\":\"open_app\",\"package\":\"包名\"}</agent_action>"
                + "\n其中 action 仅允许：open_app（须带 package）、open_settings、open_wifi_settings。"
                + "open_app 的 package 必须从下方「已安装应用列表」中选取，禁止编造未列出的包名。"
                + "若用户要开的应用不在列表中，说明未找到并建议检查应用名，不要输出 agent_action。"
                + "\n已安装应用列表（显示名|包名，每行一个）：\n"
                + appList;
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
