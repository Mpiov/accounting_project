package com.autobook.cn.service.notification

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 把通知监听服务的关键生命周期和回调事件写入本地 trace 文件，便于在真机上排查系统是否真的分发了回调。
 *
 * 参数说明：
 * - context：应用上下文，用于定位沙箱内的缓存目录。
 * - message：本次要追加写入的诊断信息。
 */
internal object NotificationListenerTraceRecorder {
    /**
     * 串行写入作用域。
     *
     * 用途说明：
     * - 所有 trace 文件操作统一排队到单一后台消费者，避免在服务回调线程上直接做磁盘 I/O。
     * - 这样既能保持写入顺序，也能避免通知风暴时把主线程拖慢。
     */
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 后台写入命令通道。
     *
     * 用途说明：
     * - `Channel.UNLIMITED` 可以保证高频通知到来时先快速入队，不在调用线程上阻塞。
     * - 真正的清空与追加写文件统一由单一协程顺序消费。
     */
    private val commandChannel = Channel<TraceCommand>(capacity = Channel.UNLIMITED)

    init {
        writerScope.launch {
            for (command in commandChannel) {
                when (command) {
                    is TraceCommand.Clear -> clearNow(context = command.context)
                    is TraceCommand.Record -> recordNow(
                        context = command.context,
                        message = command.message,
                    )
                }
            }
        }
    }

    fun record(
        context: Context,
        message: String,
    ) {
        commandChannel.trySend(
            TraceCommand.Record(
                context = context.applicationContext,
                message = message,
            ),
        )
    }

    /**
     * 清空旧的诊断文件，保证每一轮排查只保留最新一次测试的内容。
     *
     * 参数说明：
     * - context：应用上下文，用于定位沙箱内的缓存目录。
     */
    fun clear(context: Context) {
        commandChannel.trySend(TraceCommand.Clear(context = context.applicationContext))
    }

    /**
     * 真正执行追加写入。
     *
     * 参数说明：
     * - context：应用上下文，用于定位缓存目录。
     * - message：要落盘的诊断信息。
     */
    private fun recordNow(
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
     * 真正执行清空操作。
     *
     * 参数说明：
     * - context：应用上下文，用于定位缓存目录。
     */
    private fun clearNow(context: Context) {
        runCatching {
            val traceFile = context.cacheDir.resolve(TRACE_FILE_NAME)
            if (traceFile.exists()) {
                traceFile.writeText("")
            }
        }
    }
}

/**
 * trace 落盘命令。
 *
 * 用途说明：
 * - 把“清空文件”和“追加日志”统一抽象成串行消费的命令，确保后台执行顺序稳定。
 */
private sealed interface TraceCommand {
    /**
     * 追加一条 trace。
     *
     * 参数说明：
     * - context：应用上下文。
     * - message：待落盘诊断文案。
     */
    data class Record(
        val context: Context,
        val message: String,
    ) : TraceCommand

    /**
     * 清空 trace 文件。
     *
     * 参数说明：
     * - context：应用上下文。
     */
    data class Clear(
        val context: Context,
    ) : TraceCommand
}

/** 真机排查时统一写入这一份 trace 文件，方便 adb 直接导出查看。 */
private const val TRACE_FILE_NAME = "notification-listener-trace.log"

/** 诊断日志只需要到秒级时间粒度，既方便阅读，也足以对齐一次支付测试。 */
private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
