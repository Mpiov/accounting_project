package com.autobook.cn.core.data.impl

import com.autobook.cn.core.model.AuthSession
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.network.BackendEnvelope
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/**
 * 用途说明：
 * - 封装后端统一响应外壳到客户端 OperationResult 的转换逻辑。
 *
 * 参数说明：
 * - data：服务端业务数据。
 * - serverTime：服务端返回的当前时间。
 */
internal data class BackendPayload<T : Any>(
    val data: T,
    val serverTime: Long,
)

/**
 * 用途说明：
 * - 执行业务后端调用，并把 HTTP、业务错误和网络异常统一映射成 OperationResult。
 *
 * 参数说明：
 * - endpointLabel：当前接口标签，用于错误消息定位。
 * - block：实际 Retrofit 调用。
 */
internal suspend fun <T : Any> callBackend(
    endpointLabel: String,
    block: suspend () -> Response<BackendEnvelope<T>>,
): OperationResult<BackendPayload<T>> {
    return try {
        val response = block()
        val envelope = response.body()
        if (response.isSuccessful && envelope?.success == true) {
            val data = envelope.data
                ?: return OperationResult.TerminalFailure("$endpointLabel 返回空数据")
            return OperationResult.Success(
                BackendPayload(
                    data = data,
                    serverTime = envelope.serverTime,
                ),
            )
        }

        val message = envelope?.error?.message
            ?: response.errorBody()?.string()?.extractBackendErrorMessage()
            ?: "$endpointLabel 请求失败：HTTP ${response.code()}"
        if (response.code() >= 500) {
            OperationResult.RetryableFailure(message)
        } else {
            OperationResult.TerminalFailure(message)
        }
    } catch (exception: IOException) {
        OperationResult.RetryableFailure("无法连接服务器，请检查网络后重试", exception)
    } catch (exception: Throwable) {
        OperationResult.RetryableFailure("服务器响应异常：${exception.message ?: exception.javaClass.simpleName}", exception)
    }
}

/**
 * 用途说明：
 * - 拼装 Bearer 鉴权头。
 *
 * 参数说明：
 * - session：当前本地保存的登录会话。
 */
internal fun AuthSession.authorizationHeader(): String = "Bearer $accessToken"

/**
 * 用途说明：
 * - 从后端错误响应文本里提取业务错误消息。
 *
 * 参数说明：
 * - 接收者：HTTP error body 原文。
 */
private fun String.extractBackendErrorMessage(): String? {
    return runCatching {
        BACKEND_JSON.parseToJsonElement(this)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()
}

/**
 * 用途说明：
 * - 后端响应 JSON 解析器。
 */
private val BACKEND_JSON = Json {
    ignoreUnknownKeys = true
}
