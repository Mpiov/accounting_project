package com.autobook.cn.core.data.impl

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autobook.cn.core.data.CloudConfidenceModel
import com.autobook.cn.core.data.CloudModelStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudModelDataStore by preferencesDataStore(name = "autobook_cloud_model")

/**
 * 用途说明：
 * - 使用 DataStore 保存服务器发布的全局轻量模型。
 * - 模型体积很小，只包含版本号、权重、阈值和发布时间，适合放在 Preferences DataStore 中。
 *
 * 参数说明：
 * - context：应用级上下文，用于访问模型专用 DataStore。
 */
@Singleton
class CloudModelStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CloudModelStore {
    /**
     * 用途说明：
     * - DataStore 键定义集中收口，避免模型缓存字段散落在业务代码里。
     */
    private object Keys {
        val modelVersion = stringPreferencesKey("model_version")
        val modelScope = stringPreferencesKey("model_scope")
        val modelType = stringPreferencesKey("model_type")
        val weights = stringPreferencesKey("weights")
        val thresholds = stringPreferencesKey("thresholds")
        val publishedAt = longPreferencesKey("published_at")
        val validUntil = longPreferencesKey("valid_until")
        val trainedSampleCount = intPreferencesKey("trained_sample_count")
        val signature = stringPreferencesKey("signature")
    }

    override val activeModel: Flow<CloudConfidenceModel?> = context.cloudModelDataStore.data.map { preferences ->
        preferences.toCloudConfidenceModel()
    }

    /**
     * 用途说明：
     * - 读取当前模型快照，供解析器和模型拉取接口判断本地版本。
     */
    override suspend fun getActiveModel(): CloudConfidenceModel? = activeModel.first()

    /**
     * 用途说明：
     * - 保存服务器发布的全局模型。
     *
     * 参数说明：
     * - model：待落盘模型；空版本号或空权重不应调用到这里，上层会先做基础校验。
     */
    override suspend fun saveGlobalModel(model: CloudConfidenceModel) {
        context.cloudModelDataStore.edit { preferences ->
            preferences[Keys.modelVersion] = model.modelVersion
            preferences[Keys.modelScope] = model.modelScope
            model.modelType?.takeIf(String::isNotBlank)?.let { preferences[Keys.modelType] = it }
                ?: preferences.remove(Keys.modelType)
            preferences[Keys.weights] = serializeDoubleMap(model.weights)
            preferences[Keys.thresholds] = serializeDoubleMap(model.thresholds)
            model.publishedAt?.let { preferences[Keys.publishedAt] = it }
                ?: preferences.remove(Keys.publishedAt)
            model.validUntil?.let { preferences[Keys.validUntil] = it }
                ?: preferences.remove(Keys.validUntil)
            preferences[Keys.trainedSampleCount] = model.trainedSampleCount
            model.signature?.takeIf(String::isNotBlank)?.let { preferences[Keys.signature] = it }
                ?: preferences.remove(Keys.signature)
        }
    }

    /**
     * 用途说明：
     * - 清空本地模型缓存，解析器下次会自动回退内置模型。
     */
    override suspend fun clearActiveModel() {
        context.cloudModelDataStore.edit { preferences -> preferences.clear() }
    }

    /**
     * 用途说明：
     * - 从 DataStore 快照恢复领域模型。
     *
     * 参数说明：
     * - 接收者：DataStore 当前偏好快照。
     */
    private fun Preferences.toCloudConfidenceModel(): CloudConfidenceModel? {
        val modelVersion = this[Keys.modelVersion]?.takeIf(String::isNotBlank) ?: return null
        return CloudConfidenceModel(
            modelVersion = modelVersion,
            modelScope = this[Keys.modelScope]?.takeIf(String::isNotBlank) ?: "GLOBAL",
            modelType = this[Keys.modelType]?.takeIf(String::isNotBlank),
            weights = deserializeDoubleMap(this[Keys.weights].orEmpty()),
            thresholds = deserializeDoubleMap(this[Keys.thresholds].orEmpty()),
            publishedAt = this[Keys.publishedAt],
            validUntil = this[Keys.validUntil],
            trainedSampleCount = this[Keys.trainedSampleCount] ?: 0,
            signature = this[Keys.signature]?.takeIf(String::isNotBlank),
        )
    }
}

/**
 * 用途说明：
 * - 把模型权重 Map 序列化成稳定的行文本，避免引入额外 JSON 依赖。
 *
 * 参数说明：
 * - values：待保存的权重或阈值表。
 */
internal fun serializeDoubleMap(values: Map<String, Double>): String {
    return values
        .filter { (_, value) -> value.isFinite() }
        .toSortedMap()
        .entries
        .joinToString(separator = "\n") { (key, value) ->
            "${urlEncode(key)}=${String.format(Locale.ROOT, "%.12f", value)}"
        }
}

/**
 * 用途说明：
 * - 从 DataStore 行文本恢复模型权重 Map。
 *
 * 参数说明：
 * - rawValue：`serializeDoubleMap` 生成的原始文本。
 */
internal fun deserializeDoubleMap(rawValue: String): Map<String, Double> {
    if (rawValue.isBlank()) {
        return emptyMap()
    }
    return rawValue
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0 || separatorIndex == line.lastIndex) {
                return@mapNotNull null
            }
            val key = urlDecode(line.substring(0, separatorIndex)).takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val value = line.substring(separatorIndex + 1).toDoubleOrNull()
                ?.takeIf { parsedValue -> parsedValue.isFinite() }
                ?: return@mapNotNull null
            key to value
        }
        .toMap()
}

/**
 * 用途说明：
 * - 对 Map 键做 URL 编码，保证换行、等号和中文键都能安全保存。
 *
 * 参数说明：
 * - value：待编码字符串。
 */
private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

/**
 * 用途说明：
 * - 从 DataStore 文本中还原 Map 键。
 *
 * 参数说明：
 * - value：待解码字符串。
 */
private fun urlDecode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
