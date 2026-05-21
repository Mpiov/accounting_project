package com.autobook.cn.core.network

import com.autobook.cn.core.model.AUTO_LEDGER_FREE_TRIAL_LIMIT
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * 用途说明：
 * - AutoBook 业务后端的 Retrofit 接口定义。
 * - 所有返回值都保留 HTTP Response，方便数据层读取服务端统一错误外壳。
 */
interface AutobookBackendApi {
    /**
     * 用途说明：
     * - 请求登录验证码。
     *
     * 参数说明：
     * - body：邮箱和验证码场景；字段名暂沿用后端旧契约。
     */
    @POST("v1/auth/sms-code")
    suspend fun requestSmsCode(
        @Body body: BackendSmsCodeRequest,
    ): Response<BackendEnvelope<BackendSmsCodeResponse>>

    /**
     * 用途说明：
     * - 使用验证码登录或注册。
     *
     * 参数说明：
     * - body：邮箱、验证码和客户端信息；字段名暂沿用后端旧契约。
     */
    @POST("v1/auth/login")
    suspend fun login(
        @Body body: BackendLoginRequest,
    ): Response<BackendEnvelope<BackendLoginResponse>>

    /**
     * 用途说明：
     * - 刷新访问令牌。
     *
     * 参数说明：
     * - body：刷新令牌。
     */
    @POST("v1/auth/refresh")
    suspend fun refreshToken(
        @Body body: BackendRefreshTokenRequest,
    ): Response<BackendEnvelope<BackendTokenPairResponse>>

    /**
     * 用途说明：
     * - 查询服务器权威会员状态。
     *
     * 参数说明：
     * - authorization：Bearer 访问令牌。
     */
    @GET("v1/membership/status")
    suspend fun membershipStatus(
        @Header("Authorization") authorization: String,
    ): Response<BackendEnvelope<BackendMembershipStatusResponse>>

    /**
     * 用途说明：
     * - 免费用户自动记账真正成功前，先让服务器占用并扣减一次免费体验额度。
     *
     * 参数说明：
     * - authorization：Bearer 访问令牌；服务端通过登录用户 ID 维护次数，客户端不再本地记数。
     */
    @POST("v1/auto-ledger/trial-success")
    suspend fun recordAutoLedgerTrialSuccess(
        @Header("Authorization") authorization: String,
    ): Response<BackendEnvelope<BackendMembershipStatusResponse>>

    /**
     * 用途说明：
     * - 创建会员订单。
     *
     * 参数说明：
     * - authorization：Bearer 访问令牌。
     * - body：套餐和支付渠道。
     */
    @POST("v1/payments/membership-orders")
    suspend fun createMembershipOrder(
        @Header("Authorization") authorization: String,
        @Body body: BackendCreateMembershipOrderRequest,
    ): Response<BackendEnvelope<BackendMembershipOrderResponse>>

    /**
     * 用途说明：
     * - 查询会员订单状态。
     *
     * 参数说明：
     * - authorization：Bearer 访问令牌。
     * - orderId：服务器订单 ID。
     */
    @GET("v1/payments/membership-orders/{orderId}")
    suspend fun queryMembershipOrder(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String,
    ): Response<BackendEnvelope<BackendMembershipOrderStateResponse>>

    /**
     * 用途说明：
     * - 上传会员贡献给服务器全局样本池的训练样本。
     *
     * 参数说明：
     * - authorization：Bearer 访问令牌。
     * - body：批量训练样本。
     */
    @POST("v1/cloud-learning/training-samples")
    suspend fun uploadTrainingSamples(
        @Header("Authorization") authorization: String,
        @Body body: BackendTrainingSamplesRequest,
    ): Response<BackendEnvelope<BackendTrainingSamplesResponse>>

    /**
     * 用途说明：
     * - 拉取服务器发布给全部会员客户端的最新全局模型。
     *
     * 参数说明：
     * - authorization：Bearer 访问令牌。
     * - currentVersion：客户端当前模型版本。
     */
    @GET("v1/cloud-learning/global-model")
    suspend fun pullGlobalModel(
        @Header("Authorization") authorization: String,
        @Query("currentVersion") currentVersion: String? = null,
    ): Response<BackendEnvelope<BackendCloudModelResponse>>

    /**
     * 用途说明：
     * - 查询服务端发布的 Android 最新稳定版元数据。
     * - 该接口不需要登录，只返回很小的 JSON，避免 4Mbps 服务器被 APK 自动下载流量压住。
     *
     * 参数说明：
     * - platform：客户端平台，当前固定为 android。
     * - channel：发布通道，当前固定为 stable。
     * - currentVersionCode：当前安装包 versionCode，用于服务端判断是否有新版本。
     * - currentVersionName：当前安装包展示版本名，便于服务端记录和排查。
     */
    @GET("v1/app/releases/latest")
    suspend fun latestAppRelease(
        @Query("currentVersionCode") currentVersionCode: Int,
        @Query("currentVersionName") currentVersionName: String,
        @Query("platform") platform: String = "android",
        @Query("channel") channel: String = "stable",
    ): Response<BackendEnvelope<BackendAppReleaseResponse>>

    /**
     * 用途说明：
     * - 提交用户在设置页填写的产品反馈。
     *
     * 参数解释：
     * - authorization：Bearer 访问令牌，用于把反馈绑定到服务器账号。
     * - body：反馈正文、联系方式、设备与版本信息。
     */
    @POST("v1/user-feedback")
    suspend fun submitUserFeedback(
        @Header("Authorization") authorization: String,
        @Body body: BackendUserFeedbackRequest,
    ): Response<BackendEnvelope<BackendUserFeedbackResponse>>
}

/**
 * 用途说明：
 * - 服务端统一响应外壳。
 *
 * 参数说明：
 * - requestId：服务端请求 ID。
 * - success：业务是否成功。
 * - data：业务数据。
 * - error：失败时的业务错误。
 * - serverTime：服务器毫秒时间。
 */
data class BackendEnvelope<T>(
    val requestId: String = "",
    val success: Boolean = false,
    val data: T? = null,
    val error: BackendErrorResponse? = null,
    val serverTime: Long = 0L,
)

/**
 * 用途说明：
 * - 服务端业务错误。
 *
 * 参数说明：
 * - code：业务错误码。
 * - message：错误说明。
 */
data class BackendErrorResponse(
    val code: String = "",
    val message: String = "",
)

/** 用途说明：邮箱验证码请求体；接口路径暂沿用 `/v1/auth/sms-code`。 */
data class BackendSmsCodeRequest(
    val phone: String,
    val scene: String = "login",
)

/**
 * 用途说明：
 * - 邮箱验证码响应体，只描述验证码有效期。
 *
 * 参数说明：
 * - expiresInSeconds：验证码从创建到过期的秒数；获取按钮的 60 秒等待由客户端本地限流维护。
 */
data class BackendSmsCodeResponse(
    val expiresInSeconds: Int = 0,
)

/** 用途说明：验证码登录请求体。 */
data class BackendLoginRequest(
    val phone: String,
    val code: String,
    val deviceId: String,
    val appVersion: String,
)

/** 用途说明：刷新令牌请求体。 */
data class BackendRefreshTokenRequest(
    val refreshToken: String,
)

/**
 * 用途说明：
 * - 登录成功响应体。
 */
data class BackendLoginResponse(
    val userId: String,
    val phone: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long,
    val refreshTokenExpiresAt: Long,
    val isNewUser: Boolean = false,
)

/** 用途说明：令牌刷新响应体。 */
data class BackendTokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long,
    val refreshTokenExpiresAt: Long,
)

/** 用途说明：服务器权威会员状态响应体。 */
data class BackendMembershipStatusResponse(
    val userId: String? = null,
    val phone: String? = null,
    val tier: String = "FREE",
    val expiresAt: Long? = null,
    val modelConfidenceEnabled: Boolean = false,
    val cloudLearningEnabled: Boolean = false,
    val autoLedgerTrialLimit: Int = AUTO_LEDGER_FREE_TRIAL_LIMIT,
    val autoLedgerTrialUsed: Int = 0,
    val autoLedgerTrialRemaining: Int = AUTO_LEDGER_FREE_TRIAL_LIMIT,
    val serverVerified: Boolean = false,
)

/** 用途说明：创建会员订单请求体。 */
data class BackendCreateMembershipOrderRequest(
    val plan: String,
    val payChannel: String = "WECHAT_APP",
)

/** 用途说明：会员订单响应体。 */
data class BackendMembershipOrderResponse(
    val orderId: String,
    val plan: String,
    val amountCents: Long,
    val paymentState: String = "CREATED",
    val expiresAt: Long? = null,
    val wechatAppPayParams: BackendWechatAppPayParams? = null,
)

/** 用途说明：微信 App 支付调起参数。 */
data class BackendWechatAppPayParams(
    val appId: String = "",
    val partnerId: String = "",
    val prepayId: String = "",
    val packageValue: String = "",
    val nonceStr: String = "",
    val timestamp: String = "",
    val sign: String = "",
)

/** 用途说明：会员订单状态响应体。 */
data class BackendMembershipOrderStateResponse(
    val orderId: String,
    val paymentState: String,
    val paidAt: Long? = null,
    val expiresAt: Long? = null,
)

/** 用途说明：上传到服务器全局样本池的训练样本请求体。 */
data class BackendTrainingSamplesRequest(
    val clientBatchId: String,
    val deviceId: String,
    val appVersion: String,
    val samples: List<Map<String, @JvmSuppressWildcards Any?>>,
)

/** 用途说明：全局样本池上传响应体。 */
data class BackendTrainingSamplesResponse(
    val accepted: Int = 0,
    val updated: Int = 0,
    val duplicated: Int = 0,
    val rejected: Int = 0,
    val nextCursor: String? = null,
)

/** 用途说明：服务器全局模型响应体。 */
data class BackendCloudModelResponse(
    val modelVersion: String = "",
    val modelScope: String = "GLOBAL",
    val modelType: String? = null,
    val validUntil: Long? = null,
    val minAppVersion: String? = null,
    val trainedSampleCount: Int = 0,
    val publishedAt: Long? = null,
    val weights: Map<String, Double> = emptyMap(),
    val thresholds: Map<String, Double> = emptyMap(),
    val signature: String? = null,
    val notModified: Boolean = false,
)

/**
 * 用途说明：
 * - 服务端 App 更新元数据响应体。
 *
 * 参数说明：
 * - latestVersionCode：服务端最新 Android versionCode。
 * - latestVersionName：服务端最新 Android versionName。
 * - minSupportedVersionCode：最低建议版本号。
 * - forceUpdate：是否提示强更新。
 * - apkUrl：APK 下载地址，客户端只负责交给浏览器打开。
 * - apkSizeBytes：APK 大小，单位字节。
 * - sha256：APK 摘要，当前先展示/保留，后续 App 内下载时可用于校验。
 * - releaseNotes：版本说明。
 * - publishedAt：发布时间戳，单位毫秒。
 * - notModified：当前客户端是否已经是最新版本。
 */
data class BackendAppReleaseResponse(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minSupportedVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val apkUrl: String = "",
    val apkSizeBytes: Long = 0L,
    val sha256: String? = null,
    val releaseNotes: List<String> = emptyList(),
    val publishedAt: Long? = null,
    val notModified: Boolean = true,
)

/**
 * 用途说明：
 * - 用户反馈提交请求体。
 *
 * 参数解释：
 * - content：用户填写的反馈正文。
 * - contact：用户额外留下的联系方式，可为空；登录邮箱由服务端根据令牌绑定。
 * - deviceId：客户端稳定设备标识，用于定位机型或单设备复现问题。
 * - appVersion：客户端当前版本号，方便后台判断反馈来自哪个安装包。
 */
data class BackendUserFeedbackRequest(
    val content: String,
    val contact: String? = null,
    val deviceId: String? = null,
    val appVersion: String? = null,
)

/**
 * 用途说明：
 * - 用户反馈提交响应体。
 *
 * 参数解释：
 * - feedbackId：服务器反馈主键，供后台排查和日志关联。
 * - receivedAt：服务器收到反馈的毫秒时间戳。
 */
data class BackendUserFeedbackResponse(
    val feedbackId: Long = 0L,
    val receivedAt: Long = 0L,
)

/**
 * 用途说明：
 * - 把本地 JSONL 训练样本转换成 Retrofit/Gson 可直接序列化的 Map，随后上传到服务器全局样本池。
 */
object BackendTrainingSampleParser {
    /**
     * 用途说明：
     * - 解析每行一个 JSON 对象的训练样本。
     *
     * 参数说明：
     * - jsonl：本地训练样本仓储导出的 JSONL 文本。
     */
    fun parseJsonl(jsonl: String): List<Map<String, Any?>> {
        if (jsonl.isBlank()) {
            return emptyList()
        }
        return jsonl.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                JsonParser.parseString(line)
                    .takeIf(JsonElement::isJsonObject)
                    ?.asJsonObject
                    ?.toPlainMap()
            }
            .toList()
    }
}

/**
 * 用途说明：
 * - 把 JsonObject 递归转换成普通 Map，避免数据层直接依赖 Gson 类型。
 */
private fun JsonObject.toPlainMap(): Map<String, Any?> = entrySet().associate { (key, value) ->
    key to value.toPlainValue()
}

/**
 * 用途说明：
 * - 把 Gson JsonElement 转成 Kotlin 基础类型，便于再次序列化成请求体。
 */
private fun JsonElement.toPlainValue(): Any? = when {
    this is JsonNull || isJsonNull -> null
    isJsonObject -> asJsonObject.toPlainMap()
    isJsonArray -> asJsonArray.toPlainList()
    isJsonPrimitive -> {
        val primitive = asJsonPrimitive
        when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asNumber
            else -> primitive.asString
        }
    }
    else -> null
}

/**
 * 用途说明：
 * - 把 JsonArray 转换成普通列表。
 */
private fun JsonArray.toPlainList(): List<Any?> = map { item -> item.toPlainValue() }

/**
 * 用途说明：
 * - 后端网络依赖注入模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object AutobookBackendNetworkModule {
    /**
     * 用途说明：
     * - 创建后端 API 使用的 OkHttpClient。
     */
    @Provides
    @Singleton
    fun provideAutobookBackendOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 只记录基础请求行，避免通知原文训练样本出现在本地日志里。
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(BACKEND_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BACKEND_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(BACKEND_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * 用途说明：
     * - 创建业务后端 Retrofit 实例。
     *
     * 参数说明：
     * - client：后端专用 OkHttpClient。
     */
    @Provides
    @Singleton
    fun provideAutobookBackendRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.AUTOBOOK_BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 用途说明：
     * - 创建后端 API 代理对象。
     *
     * 参数说明：
     * - retrofit：业务后端 Retrofit 实例。
     */
    @Provides
    @Singleton
    fun provideAutobookBackendApi(retrofit: Retrofit): AutobookBackendApi {
        return retrofit.create(AutobookBackendApi::class.java)
    }
}

private const val BACKEND_CONNECT_TIMEOUT_SECONDS = 10L
private const val BACKEND_READ_TIMEOUT_SECONDS = 30L
private const val BACKEND_WRITE_TIMEOUT_SECONDS = 30L
