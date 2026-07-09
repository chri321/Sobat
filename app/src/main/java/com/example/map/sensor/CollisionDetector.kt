package com.example.map.sensor

import kotlin.math.sqrt

/**
 * 碰撞检测核心算法
 *
 * 通过分析加速度传感器数据，检测是否发生碰撞/急刹车/摔倒等异常事件。
 *
 * 检测原理：
 * 1. 使用线性加速度（TYPE_LINEAR_ACCELERATION），排除重力分量
 * 2. 计算加速度向量幅值: magnitude = sqrt(x² + y² + z²)
 * 3. 当幅值超过碰撞阈值且持续多帧时，判定为碰撞事件
 * 4. 内置冷却期，避免重复触发
 */
class CollisionDetector(
    /** 碰撞加速度阈值 (m/s²)，默认约 2g */
    private val collisionThreshold: Float = 20f,
    /** 警告加速度阈值 (m/s²)，默认约 1.2g */
    private val warningThreshold: Float = 12f,
    /** 碰撞确认所需连续帧数 */
    private val confirmationFrames: Int = 3,
    /** 冷却时间 (毫秒)，两次碰撞事件最小间隔 */
    private val cooldownMs: Long = 10_000L
) {
    /** 当前加速度幅值 */
    var currentMagnitude: Float = 0f
        private set

    /** 历史峰值加速度 */
    var peakMagnitude: Float = 0f
        private set

    /** 连续超过碰撞阈值的帧数计数 */
    private var consecutiveCollisionFrames: Int = 0

    /** 连续超过警告阈值的帧数计数 */
    private var consecutiveWarningFrames: Int = 0

    /** 上次触发碰撞事件的时间戳 */
    private var lastCollisionTimestamp: Long = 0L

    /** 碰撞事件回调 */
    var onCollisionDetected: (() -> Unit)? = null

    /** 警告事件回调（剧烈晃动但未达碰撞级别） */
    var onWarningDetected: (() -> Unit)? = null

    /** 当前风险等级 */
    var riskLevel: RiskLevel = RiskLevel.SAFE
        private set

    /**
     * 处理一帧加速度数据
     * @param x X轴线性加速度 (m/s²)
     * @param y Y轴线性加速度 (m/s²)
     * @param z Z轴线性加速度 (m/s²)
     */
    fun processAccelerometerData(x: Float, y: Float, z: Float) {
        // 计算幅值
        val magnitude = sqrt(x * x + y * y + z * z)
        currentMagnitude = magnitude

        // 更新历史峰值
        if (magnitude > peakMagnitude) {
            peakMagnitude = magnitude
        }

        // 碰撞检测
        if (magnitude >= collisionThreshold) {
            consecutiveCollisionFrames++
            consecutiveWarningFrames = 0 // 重置警告计数

            if (consecutiveCollisionFrames >= confirmationFrames) {
                triggerCollision()
                consecutiveCollisionFrames = 0
            }
        } else {
            consecutiveCollisionFrames = 0
        }

        // 警告检测（仅在不处于碰撞检测状态时）
        if (consecutiveCollisionFrames == 0) {
            if (magnitude >= warningThreshold) {
                consecutiveWarningFrames++
                if (consecutiveWarningFrames >= confirmationFrames) {
                    triggerWarning()
                    consecutiveWarningFrames = 0
                }
            } else {
                consecutiveWarningFrames = 0
            }
        }

        // 更新风险等级
        riskLevel = when {
            magnitude >= collisionThreshold -> RiskLevel.DANGER
            magnitude >= warningThreshold -> RiskLevel.WARNING
            magnitude >= 5f -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }
    }

    private fun triggerCollision() {
        val now = System.currentTimeMillis()
        if (now - lastCollisionTimestamp < cooldownMs) return

        lastCollisionTimestamp = now
        riskLevel = RiskLevel.COLLISION
        onCollisionDetected?.invoke()
    }

    private fun triggerWarning() {
        riskLevel = RiskLevel.WARNING
        onWarningDetected?.invoke()
    }

    /** 重置峰值和历史数据 */
    fun resetPeak() {
        peakMagnitude = 0f
    }

    /** 完全重置检测器状态 */
    fun reset() {
        currentMagnitude = 0f
        peakMagnitude = 0f
        consecutiveCollisionFrames = 0
        consecutiveWarningFrames = 0
        riskLevel = RiskLevel.SAFE
    }
}

/**
 * 骑行风险等级
 */
enum class RiskLevel(val label: String, val severity: Int) {
    /** 安全 - 正常骑行 */
    SAFE("安全", 0),
    /** 注意 - 轻微颠簸 */
    CAUTION("注意", 1),
    /** 警告 - 剧烈晃动 */
    WARNING("警告", 2),
    /** 危险 - 接近碰撞阈值 */
    DANGER("危险", 3),
    /** 碰撞 - 已触发碰撞检测 */
    COLLISION("碰撞", 4)
}
