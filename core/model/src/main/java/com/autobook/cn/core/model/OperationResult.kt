package com.autobook.cn.core.model

sealed interface OperationResult<out T> {
    data class Success<T>(val data: T) : OperationResult<T>

    data class RetryableFailure(
        val message: String,
        val cause: Throwable? = null,
    ) : OperationResult<Nothing>

    data class TerminalFailure(
        val message: String,
        val cause: Throwable? = null,
    ) : OperationResult<Nothing>
}
