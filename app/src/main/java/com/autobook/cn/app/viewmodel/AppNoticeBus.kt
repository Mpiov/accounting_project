package com.autobook.cn.app.viewmodel

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 用途说明：
 * - 提供全局底部提示条事件通道，让登录成功、模型拉取成功、体验次数耗尽等轻提示复用同一套 UI。
 * - 使用 SharedFlow 而不是 StateFlow，避免页面重组时把已经展示过的一次性提示重复展示。
 *
 * 参数说明：
 * - 无构造参数；由 Hilt 作为单例注入到各个 ViewModel。
 */
@Singleton
class AppNoticeBus @Inject constructor() {
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = NOTICE_BUFFER_CAPACITY)

    /** 用途说明：供壳层收集的一次性提示事件流。 */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /**
     * 用途说明：
     * - 发送一条全局底部提示。
     *
     * 参数说明：
     * - message：需要展示给用户的短提示文案；空白文案会被忽略。
     */
    fun show(message: String) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isBlank()) {
            return
        }
        // 提示条是辅助反馈，缓冲区满时丢弃最旧之外的即时提示即可，不能阻塞业务流程。
        _notices.tryEmit(normalizedMessage)
    }
}

/** 用途说明：限制提示事件缓冲长度，避免连续操作时无界排队。 */
private const val NOTICE_BUFFER_CAPACITY = 16
