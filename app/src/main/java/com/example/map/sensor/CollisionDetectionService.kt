package com.example.map.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.map.MainActivity
import com.example.map.R
import com.example.map.SettingsActivity

/**
 * 碰撞检测前台服务
 *
 * 后台持续运行，监测加速度传感器数据，> 2g 碰撞时触发告警。
 * 通过 SharedPreferences 中的 "collision_detection_enabled" 开关控制启停。
 */
class CollisionDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var linearAccelerometer: Sensor? = null

    private val collisionDetector = CollisionDetector(
        collisionThreshold = 20f, warningThreshold = 12f,
        confirmationFrames = 3, cooldownMs = 10_000L
    )

    companion object {
        const val CHANNEL_ID = "collision_detection_channel"
        const val ALERT_CHANNEL_ID = "collision_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val EXTRA_COLLISION_ALERT = "extra_collision_alert"

        /** SharedPreferences key for enable toggle */
        const val PREF_COLLISION_ENABLED = "collision_detection_enabled"
        const val PREF_NAME = "collision_settings"

        /** 重力向量初始值（用于低通滤波分离重力） */
        private val gravity = floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH)

        private var _instance: CollisionDetectionService? = null
        fun getInstance(): CollisionDetectionService? = _instance
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        collisionDetector.onCollisionDetected = { onCollisionDetected() }
        collisionDetector.onWarningDetected = { onWarningDetected() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startSensorMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _instance = null
        stopSensorMonitoring()
        super.onDestroy()
    }

    // ==================== 传感器 ====================

    private fun startSensorMonitoring() {
        val sensor = linearAccelerometer ?: accelerometer
        if (sensor == null) return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val data = when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                Triple(event.values[0], event.values[1], event.values[2])
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // 低通滤波分离重力
                val alpha = 0.8f
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                Triple(
                    event.values[0] - gravity[0],
                    event.values[1] - gravity[1],
                    event.values[2] - gravity[2]
                )
            }
            else -> return
        }
        collisionDetector.processAccelerometerData(data.first, data.second, data.third)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ==================== 碰撞响应 ====================

    private val collisionOverlay by lazy { CollisionOverlay(this) }

    private fun onCollisionDetected() {
        triggerVibration(longArrayOf(0, 500, 200, 500, 200, 800))

        // 1. 优先：启动 MainActivity 带碰撞标记
        launchCollisionActivity()

        // 2. 增强：悬浮窗覆盖层（如果已授权）
        tryShowOverlay()

        // 3. 兜底：通知
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(ALERT_NOTIFICATION_ID, buildAlertNotification())
        } catch (_: Exception) {}
    }

    private fun tryShowOverlay() {
        if (!collisionOverlay.hasPermission()) return
        try {
            collisionOverlay.show(onCancel = { /* 用户取消 */ })
        } catch (_: Exception) {
            // 悬浮窗创建失败不影响 Activity 方案
        }
    }

    private fun launchCollisionActivity() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_COLLISION_ALERT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        } catch (_: Exception) {}
    }

    private fun onWarningDetected() {
        triggerVibration(longArrayOf(0, 100, 50, 100))
    }

    // ==================== 振动 ====================

    private fun triggerVibration(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {}
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 前台服务常驻频道
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "碰撞检测服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "后台持续监测碰撞风险"
            setShowBadge(false)
        })
        // 碰撞预警频道
        nm.createNotificationChannel(NotificationChannel(
            ALERT_CHANNEL_ID, "碰撞预警", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "碰撞检测告警通知"
            enableVibration(true)
        })
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("碰撞检测")
            .setContentText("安全监测中，正在持续检测碰撞风险")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun buildAlertNotification(): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_COLLISION_ALERT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("疑似检测到车祸！")
            .setContentText("请点击确认安全")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()
    }
}
