package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.AuthRepository
import com.autobook.cn.core.model.AuthSession
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.network.BackendEnvelope
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.first
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

        val errorSnapshot = response.errorBody()?.string()?.extractBackendErrorSnapshot()
        val code = envelope?.error?.code ?: errorSnapshot?.code
        val message = envelope?.error?.message
            ?: errorSnapshot?.message
            ?: "$endpointLabel 请求失败：HTTP ${response.code()}"
        if (response.code() >= 500) {
            OperationResult.RetryableFailure(message = message, code = code)
        } else {
            OperationResult.TerminalFailure(message = message, code = code)
        }
    } catch (exception: IOException) {
        OperationResult.RetryableFailure("无法连接服务器，请检查网络后重试", exception)
    } catch (exception: Throwable) {
        OperationResult.RetryableFailure("服务器响应异常：${exception.message ?: exception.javaClass.simpleName}", exception)
    }
}

/**
 * 用途说明：
 * - 执行需要登录态的后端业务调用。
 * - 首次请求仍然使用当前 access token；只有服务端返回 `AUTH_TOKEN_EXPIRED` 时，才用 refresh token 刷新并重试一次。
 *
 * 参数说明：
 * - endpointLabel：当前接口标签，用于错误消息定位。
 * - authRepository：账号仓储，用于读取和刷新本地会话。
 * - loginRequiredMessage：本地没有会话时返回给用户的提示。
 * - block：真实 Retrofit 调用，入参是已经拼好的 Authorization 头。
 */
internal suspend fun <T : Any> callAuthenticatedBackend(
    endpointLabel: String,
    authRepository: AuthRepository,
    loginRequiredMessage: String,
    block: suspend (String) -> Response<BackendEnvelope<T>>,
): OperationResult<BackendPayload<T>> {
    val session = authRepository.authState.first().session
        ?: return OperationResult.TerminalFailure(loginRequiredMessage)
    val firstResult = callBackend(endpointLabel) {
        block(session.authorizationHeader())
    }
    if (firstResult !is OperationResult.TerminalFailure || firstResult.code != AUTH_TOKEN_EXPIRED_CODE) {
        return firstResult
    }
    return when (val refreshResult = authRepository.refreshSession(expiredAccessToken = session.accessToken)) {
        is OperationResult.Success -> callBackend(endpointLabel) {
            block(refreshResult.data.authorizationHeader())
        }
        is OperationResult.RetryableFailure -> refreshResult
        is OperationResult.TerminalFailure -> refreshResult
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

/** 用途说明：后端表示 access token 已过期的稳定错误码。 */
internal const val AUTH_TOKEN_EXPIRED_CODE = "AUTH_TOKEN_EXPIRED"

/**
 * 用途说明：
 * - 保存后端错误响应里的业务错误码和文案。
 *
 * 参数说明：
 * - code：后端稳定错误码，例如 `AUTH_TOKEN_EXPIRED`。
 * - message：可直接展示给用户的错误说明。
 */
private data class BackendErrorSnapshot(
    val code: String?,
    val message: String?,
)

/**
 * 用途说明：
 * - 从后端错误响应文本里提取业务错误码和错误消息。
 *
 * 参数说明：
 * - 接收者：HTTP error body 原文。
 */
private fun String.extractBackendErrorSnapshot(): BackendErrorSnapshot? {
    return runCatching {
        val errorObject = BACKEND_JSON.parseToJsonElement(this)
            .jsonObject["error"]
            ?.jsonObject
            ?: return@runCatching null
        BackendErrorSnapshot(
            code = errorObject["code"]?.jsonPrimitive?.contentOrNull,
            message = errorObject["message"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()
}

/**
 * 用途说明：
 * - 后端响应 JSON 解析器。
 */
private val BACKEND_JSON = Json {
    ignoreUnknownKeys = true
}
