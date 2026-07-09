package com.example.map.sensor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 系统级悬浮窗覆盖层
 *
 * 通过 WindowManager 直接在全屏最上层绘制碰撞告警界面，
 * 绕过 Android 10+ 后台启动 Activity 限制，100% 可靠弹出。
 *
 * 使用前提：用户已授权 SYSTEM_ALERT_WINDOW 权限。
 */
class CollisionOverlay(private val context: Context) {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    private var onCancelCallback: (() -> Unit)? = null

    /** 检查悬浮窗权限是否已授权 */
    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    /** 打开系统悬浮窗授权页面 */
    fun openPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 弹出全屏碰撞告警覆盖层 */
    fun show(onCancel: () -> Unit) {
        // 防止重复弹出
        if (overlayView != null) hide()

        onCancelCallback = onCancel

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }

        overlayView = createOverlayView()
        windowManager?.addView(overlayView, params)
    }

    /** 隐藏覆盖层 */
    fun hide() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        windowManager = null
    }

    private fun createOverlayView(): View {
        // 半透明暗色背景
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(180, 0, 0, 0))
        }

        // 居中卡片
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.WHITE)
            val cardParams = FrameLayout.LayoutParams(
                dp(320),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.gravity = Gravity.CENTER
            layoutParams = cardParams
        }

        // ⚠ 图标
        card.addView(TextView(context).apply {
            text = "⚠"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })

        // 标题
        card.addView(TextView(context).apply {
            text = "检测到碰撞！"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D50000"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        // 说明
        card.addView(TextView(context).apply {
            text = "系统检测到异常加速度变化，\n你是否遭遇危险？"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dp(24))
        })

        // 拨打急救电话按钮
        card.addView(createButton("拨打 120 急救电话",
            Color.parseColor("#D50000"), Color.WHITE
        ) {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:120")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        })

        // 取消
        val cancelBtn = TextView(context).apply {
            text = "取消"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, dp(16), 0, 0)
            setOnClickListener {
                hide()
                onCancelCallback?.invoke()
            }
        }
        card.addView(cancelBtn)

        root.addView(card)
        return root
    }

    private fun createButton(
        text: String, bgColor: Int, textColor: Int, onClick: () -> Unit
    ): View {
        return TextView(context).apply {
            this.text = text
            this.textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setBackgroundColor(bgColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
