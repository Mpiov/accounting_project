package com.autobook.cn.core.data.impl

import java.security.SecureRandom

/**
 * 账单展示顺序使用的分布式趋势 ID 生成器。
 *
 * 用途说明：
 * - `ledger_entries` 和 `pending_entries` 需要共用同一套趋势 ID，待确认转正式账本时才能保留原始顺序。
 * - ID 采用 Snowflake 风格：时间戳 + 本地节点号 + 毫秒内序号，适合后续多设备或云端合并时减少碰撞风险。
 */
internal object AutobookSortIdGenerator {
    /** 自定义纪元：2026-01-01 00:00:00 UTC，缩短时间部分占用位数。 */
    private const val CUSTOM_EPOCH_MILLIS = 1_767_225_600_000L

    /** 毫秒内序号位数，单进程每毫秒最多生成 4096 个账单顺序号。 */
    private const val SEQUENCE_BITS = 12

    /** 本地节点位数，用于降低不同设备离线生成同一 ID 的概率。 */
    private const val NODE_BITS = 10

    private const val MAX_SEQUENCE = (1L shl SEQUENCE_BITS) - 1L
    private const val MAX_NODE_ID = (1L shl NODE_BITS) - 1L
    private const val NODE_SHIFT = SEQUENCE_BITS
    private const val TIME_SHIFT = NODE_BITS + SEQUENCE_BITS

    /** 本地节点号当前不落盘，使用安全随机数生成，后续接设备表时可以替换成持久节点号。 */
    private val nodeId: Long = SecureRandom().nextInt((MAX_NODE_ID + 1).toInt()).toLong()

    private var lastTimestamp: Long = -1L
    private var sequence: Long = 0L

    /**
     * 生成下一枚趋势 ID。
     *
     * 返回值说明：
     * - 趋势递增且大概率全局唯一的 63 位正整数，可直接存入 SQLite `INTEGER`。
     */
    @Synchronized
    fun nextId(): Long {
        var now = System.currentTimeMillis()
        if (now < lastTimestamp) {
            // 系统时钟回拨时沿用上一毫秒，保证本进程内 ID 仍然单调递增。
            now = lastTimestamp
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1L) and MAX_SEQUENCE
            if (sequence == 0L) {
                now = waitNextMillis(lastTimestamp)
            }
        } else {
            sequence = 0L
        }

        lastTimestamp = now
        return ((now - CUSTOM_EPOCH_MILLIS) shl TIME_SHIFT) or
            (nodeId shl NODE_SHIFT) or
            sequence
    }

    /**
     * 等待进入下一毫秒。
     *
     * 参数说明：
     * - currentTimestamp：已经用满序号的毫秒时间戳。
     */
    private fun waitNextMillis(currentTimestamp: Long): Long {
        var nextTimestamp = System.currentTimeMillis()
        while (nextTimestamp <= currentTimestamp) {
            nextTimestamp = System.currentTimeMillis()
        }
        return nextTimestamp
    }
}
