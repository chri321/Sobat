package com.example.map;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI 对话页面 - 接入 DeepSeek API
 */
public class AiChatActivity extends AppCompatActivity {

    private LinearLayout layoutMessages;
    private ScrollView scrollChat;
    private EditText editInput;
    private TextView btnSend;
    private TextView btnBack;

    private DeepSeekClient deepSeekClient;
    private JSONArray messageHistory = new JSONArray();
    private boolean isWaiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        layoutMessages = findViewById(R.id.layout_messages);
        scrollChat = findViewById(R.id.scroll_chat);
        editInput = findViewById(R.id.edit_input);
        btnSend = findViewById(R.id.btn_send);
        btnBack = findViewById(R.id.btn_back);

        deepSeekClient = new DeepSeekClient(this);

        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> sendMessage());

        // 初始欢迎消息
        addBotMessage("你好！我是基于 DeepSeek 的 AI 助手。\n\n你可以问我任何问题，比如：\n• 附近有什么好玩的？\n• 帮我推荐一条自驾路线\n• 车辆故障怎么办？");
    }

    private void sendMessage() {
        String text = editInput.getText().toString().trim();
        if (text.isEmpty() || isWaiting) return;

        editInput.setText("");
        isWaiting = true;
        btnSend.setEnabled(false);

        addUserMessage(text);
        addTypingIndicator();

        JSONArray historyCopy;
        try {
            historyCopy = new JSONArray(messageHistory.toString());
        } catch (org.json.JSONException e) {
            historyCopy = new JSONArray();
        }

        deepSeekClient.chat(text, historyCopy,
                new DeepSeekClient.Callback() {
                    @Override
                    public void onResponse(String reply) {
                        removeTypingIndicator();
                        addBotMessage(reply);
                        isWaiting = false;
                        btnSend.setEnabled(true);
                    }

                    @Override
                    public void onError(String error) {
                        removeTypingIndicator();
                        addBotMessage("抱歉，出了点问题：\n" + error);
                        isWaiting = false;
                        btnSend.setEnabled(true);
                        Toast.makeText(AiChatActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addUserMessage(String text) {
        try {
            messageHistory.put(new JSONObject()
                    .put("role", "user")
                    .put("content", text));
        } catch (org.json.JSONException ignored) {}

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setBackgroundColor(Color.parseColor("#FF6A00"));
        tv.setPadding(28, 18, 28, 18);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.END);
        wrapper.addView(tv);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 24;
        wrapper.setLayoutParams(params);

        layoutMessages.addView(wrapper);
        scrollDown();
    }

    private void addBotMessage(String text) {
        try {
            messageHistory.put(new JSONObject()
                    .put("role", "assistant")
                    .put("content", text));
        } catch (org.json.JSONException ignored) {}

        TextView tv = new TextView(this);
        tv.setText(MarkdownRenderer.render(text));
        tv.setTextColor(Color.parseColor("#1A1A1A"));
        tv.setTextSize(14);
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(28, 18, 28, 18);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.START);
        wrapper.addView(tv);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 24;
        wrapper.setLayoutParams(params);

        layoutMessages.addView(wrapper);
        scrollDown();
    }

    private void addTypingIndicator() {
        TextView tv = new TextView(this);
        tv.setText("思考中…");
        tv.setTextColor(Color.parseColor("#999999"));
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        tv.setPadding(28, 14, 28, 14);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.START);
        wrapper.addView(tv);
        wrapper.setTag("typing");

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 12;
        wrapper.setLayoutParams(params);

        layoutMessages.addView(wrapper);
        scrollDown();
    }

    private void removeTypingIndicator() {
        for (int i = layoutMessages.getChildCount() - 1; i >= 0; i--) {
            View child = layoutMessages.getChildAt(i);
            if ("typing".equals(child.getTag())) {
                layoutMessages.removeView(child);
                break;
            }
        }
    }

    private void scrollDown() {
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }
}
