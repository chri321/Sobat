package com.example.map;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DeepSeek API 客户端
 * 文档: https://api-docs.deepseek.com/
 */
public class DeepSeekClient {

    public static final String PREF_NAME = "sobat_prefs";
    public static final String PREF_API_KEY = "deepseek_api_key";

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String MODEL = "deepseek-chat";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public DeepSeekClient(Context context) {
        this.context = context.getApplicationContext();
    }

    private String getApiKey() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_API_KEY, "");
    }

    public interface Callback {
        void onResponse(String reply);
        void onError(String error);
    }

    /**
     * 发送对话请求
     *
     * @param userMessage 用户消息
     * @param history     历史对话 JSON 数组 (可为 null)
     * @param callback    回调
     */
    public void chat(String userMessage, JSONArray history, Callback callback) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError("请先在设置中配置 DeepSeek API Key"));
            return;
        }

        executor.execute(() -> {
            try {
                JSONArray messages = (history != null) ? history : new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", userMessage));

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", MODEL);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.7);
                requestBody.put("max_tokens", 1024);

                HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    JSONArray choices = json.getJSONArray("choices");
                    String reply = choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    mainHandler.post(() -> callback.onResponse(reply));
                } else {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                    reader.close();
                    mainHandler.post(() -> {
                        try {
                            JSONObject errJson = new JSONObject(error.toString());
                            callback.onError(errJson.optString("error",
                                    errJson.optJSONObject("error") != null
                                            ? errJson.getJSONObject("error").optString("message", "请求失败")
                                            : "请求失败"));
                        } catch (Exception e) {
                            callback.onError("HTTP " + code + ": " + error);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("网络错误: " + e.getMessage()));
            }
        });
    }
}
