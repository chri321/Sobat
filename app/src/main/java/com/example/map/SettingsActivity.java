package com.example.map;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.map.sensor.CollisionDetectionService;

/**
 * 设置页面 - 提供碰撞检测开关
 */
public class SettingsActivity extends AppCompatActivity {

    private Switch switchCollision;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(CollisionDetectionService.PREF_NAME, MODE_PRIVATE);

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 碰撞检测开关
        switchCollision = findViewById(R.id.switch_collision);
        boolean enabled = prefs.getBoolean(CollisionDetectionService.PREF_COLLISION_ENABLED, true);
        switchCollision.setChecked(enabled);

        switchCollision.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(CollisionDetectionService.PREF_COLLISION_ENABLED, isChecked).apply();
            if (isChecked) {
                startCollisionService();
            } else {
                stopCollisionService();
            }
        });

        // 悬浮窗权限入口
        TextView tvOverlay = findViewById(R.id.tv_overlay_permission);
        tvOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show();
            }
        });
        updateOverlayStatus(tvOverlay);

        // DeepSeek API Key（独立 SharedPreferences，与 DeepSeekClient 共用）
        SharedPreferences aiPrefs = getSharedPreferences(DeepSeekClient.PREF_NAME, MODE_PRIVATE);
        EditText editApiKey = findViewById(R.id.edit_api_key);
        String savedKey = aiPrefs.getString(DeepSeekClient.PREF_API_KEY, "");
        editApiKey.setText(savedKey);
        editApiKey.setSelection(savedKey.length());

        findViewById(R.id.btn_save_key).setOnClickListener(v -> {
            String key = editApiKey.getText().toString().trim();
            aiPrefs.edit().putString(DeepSeekClient.PREF_API_KEY, key).apply();
            Toast.makeText(this, "API Key 已保存", Toast.LENGTH_SHORT).show();
        });

        // 碰撞检测参数说明（只读）
        TextView tvThreshold = findViewById(R.id.tv_threshold);
        tvThreshold.setText("2g (≈20 m/s²)");

        TextView tvFrames = findViewById(R.id.tv_frames);
        tvFrames.setText("连续 3 帧");

        TextView tvCooldown = findViewById(R.id.tv_cooldown);
        tvCooldown.setText("10 秒");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从悬浮窗授权页面返回后刷新状态
        TextView tvOverlay = findViewById(R.id.tv_overlay_permission);
        if (tvOverlay != null) {
            updateOverlayStatus(tvOverlay);
        }
    }

    private void updateOverlayStatus(TextView tv) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = Settings.canDrawOverlays(this);
            tv.setText(granted ? "已授权 ✓" : "去开启 →");
            tv.setTextColor(granted ? 0xFF4CAF50 : 0xFFFF6A00);
        }
    }

    private void startCollisionService() {
        Intent intent = new Intent(this, CollisionDetectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "碰撞检测已开启", Toast.LENGTH_SHORT).show();
    }

    private void stopCollisionService() {
        Intent intent = new Intent(this, CollisionDetectionService.class);
        stopService(intent);
        Toast.makeText(this, "碰撞检测已关闭", Toast.LENGTH_SHORT).show();
    }
}
