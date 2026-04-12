package com.autobook.cn.core.model

/** 跨模块通用的轻量结果封装，用来替代可预期失败场景下的异常。 */
sealed interface OperationResult<out T> {
    /** 执行成功，并携带后续步骤可直接消费的数据。 */
    data class Success<T>(val data: T) : OperationResult<T>

    /** 当前失败但具备重试价值，例如等待权限、服务恢复或外部依赖就绪。 */
    data class RetryableFailure(
        val message: String,
        val cause: Throwable? = null,
    ) : OperationResult<Nothing>

    /** 当前失败不具备直接重试价值，通常需要改代码或用户输入变化。 */
    data class TerminalFailure(
        val message: String,
        val cause: Throwable? = null,
    ) : OperationResult<Nothing>
}
