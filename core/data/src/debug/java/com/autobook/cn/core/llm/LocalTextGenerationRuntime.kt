package com.autobook.cn.core.llm

/**
 * Debug 变体下的本地文本生成请求协议。
 *
 * 用途说明：
 * - 让 `core:data` 的 debug 编译与单测在不依赖 `core:llm` 原生模块的前提下仍能拿到稳定类型。
 * - 字段保持与 release 真运行时一致，避免上层业务在 debug / release 之间分叉。
 *
 * 参数说明：
 * - modelPath：当前选中的模型路径。
 * - systemPrompt：系统提示词。
 * - userPrompt：用户提示词。
 * - maxTokens：最大输出 token 数。
 */
data class LocalTextGenerationRequest(
    val modelPath: String,
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = 192,
)

/**
 * Debug 变体下的本地文本生成结果协议。
 *
 * 用途说明：
 * - 让 debug stub 运行时与上层 reasoner 之间保持统一返回结构。
 *
 * 参数说明：
 * - text：生成结果文本。
 * - backendLabel：后端标签，用于帮助日志判断当前走的是 debug stub。
 */
data class LocalTextGenerationResult(
    val text: String,
    val backendLabel: String,
)

/**
 * Debug 变体下的本地文本生成运行时抽象。
 *
 * 用途说明：
 * - 只服务于 debug 编译与测试，不触发 `core:llm` 的 native 构建。
 * - 接口签名与 release 版本保持一致，方便上层无差别调用。
 *
 * 参数说明：
 * - request：标准化后的文本生成请求。
 */
interface LocalTextGenerationRuntime {
    suspend fun generate(request: LocalTextGenerationRequest): LocalTextGenerationResult
}
