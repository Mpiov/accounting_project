package com.autobook.cn.service.notification

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把通知监听服务的关键生命周期和回调事件写入本地 trace 文件，便于在真机上排查系统是否真的分发了回调。
 *
 * 参数说明：
 * - context：应用上下文，用于定位沙箱内的缓存目录。
 * - message：本次要追加写入的诊断信息。
 */
internal object NotificationListenerTraceRecorder {
    fun record(
        context: Context,
        message: String,
    ) {
        runCatching {
            val traceFile = context.cacheDir.resolve(TRACE_FILE_NAME)
            val timestamp = TIME_FORMAT.format(Date())
            // 每一行都带上时间戳，便于把用户操作和系统回调一一对上。
            traceFile.appendText("$timestamp $message\n")
        }
    }

    /**
     * 清空旧的诊断文件，保证每一轮排查只保留最新一次测试的内容。
     *
     * 参数说明：
     * - context：应用上下文，用于定位沙箱内的缓存目录。
     */
    fun clear(context: Context) {
        runCatching {
            val traceFile = context.cacheDir.resolve(TRACE_FILE_NAME)
            if (traceFile.exists()) {
                traceFile.writeText("")
            }
        }
    }
}

/** 真机排查时统一写入这一份 trace 文件，方便 adb 直接导出查看。 */
private const val TRACE_FILE_NAME = "notification-listener-trace.log"

/** 诊断日志只需要到秒级时间粒度，既方便阅读，也足以对齐一次支付测试。 */
private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
