package com.autobook.cn.app.debug

import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * debug 通知注入链路的共享协议。
 *
 * 用途说明：
 * - 统一 Receiver、Service 和 adb 广播之间使用的 action、extra 与默认值，避免多处手写字符串产生偏差。
 * - 把调试链路的协议收口到一个文件里，后续扩展更多测试参数时更容易维护。
 */
object DebugNotificationInjectContract {
    /** adb 直接发送的 debug 注入 action。 */
    const val ACTION_INJECT_NOTIFICATION = "com.autobook.cn.debug.INJECT_NOTIFICATION"

    /** Receiver 转交给后台 Service 的内部 action。 */
    const val ACTION_PROCESS_INJECTION = "com.autobook.cn.debug.PROCESS_INJECTION"

    /** trace 文件名。 */
    const val TRACE_FILE_NAME = "debug-notification-inject-trace.log"

    /** 日志标签。 */
    const val TAG = "AutobookDebugInject"

    /** 调试参数：通知来源包名。 */
    const val EXTRA_PACKAGE_NAME = "package_name"

    /** 调试参数：通知标题。 */
    const val EXTRA_TITLE = "title"

    /** 调试参数：通知正文。 */
    const val EXTRA_TEXT = "text"

    /** 调试参数：通知时间戳。 */
    const val EXTRA_POSTED_AT = "posted_at"

    /** 调试参数：是否启用本地 LLM。 */
    const val EXTRA_ENABLE_LOCAL_LLM = "enable_local_llm"

    /** 调试参数：是否启用自动记账。 */
    const val EXTRA_ENABLE_AUTO_ACCOUNTING = "enable_auto_accounting"

    /** 调试参数：要选中的模型 ID。 */
    const val EXTRA_MODEL_ID = "model_id"

    /** 默认通知包名。 */
    const val DEFAULT_PACKAGE_NAME = "com.android.mms"

    /** 默认通知标题。 */
    const val DEFAULT_TITLE = "银行转账提醒"

    /** 默认通知正文。 */
    const val DEFAULT_TEXT = "您尾号1234的银行卡转出100.00元，收款人张三。"

    /** 默认模型 ID。 */
    const val DEFAULT_MODEL_ID = "qwen3_1_7b_q8_0"

    /** trace 时间格式。 */
    val TRACE_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
}

/**
 * 读取字符串 extra，并在缺失或空白时回退到默认值。
 *
 * 参数说明：
 * - key：Intent extra 的键名。
 * - defaultValue：缺失或空白时使用的默认值。
 */
fun Intent.stringExtraOrDefault(
    key: String,
    defaultValue: String,
): String {
    return getStringExtra(key)?.takeIf { it.isNotBlank() } ?: defaultValue
}
