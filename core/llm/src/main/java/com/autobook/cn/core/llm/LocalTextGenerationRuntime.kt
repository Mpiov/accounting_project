package com.autobook.cn.core.llm

/**
 * 本地文本生成请求。
 *
 * 用途说明：
 * - 把业务层已经整理好的模型路径、system prompt 与 user prompt 打包成统一输入，
 *   让 `core:data` 不需要了解 JNI 或 llama.cpp 的任何细节。
 *
 * 参数说明：
 * - modelPath：当前选中的 GGUF 模型绝对路径。
 * - systemPrompt：系统提示词，用来冻结结构化输出规范与安全边界。
 * - userPrompt：用户提示词，承载通知正文和规则层候选。
 * - maxTokens：本次生成允许的最大 token 数，避免结构化抽取任务无限生成。
 */
data class LocalTextGenerationRequest(
    val modelPath: String,
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = 192,
)

/**
 * 本地文本生成结果。
 *
 * 用途说明：
 * - 把端侧运行时的原始生成文本与必要审计信息统一返回给上层。
 *
 * 参数说明：
 * - text：模型生成的原始文本。
 * - backendLabel：运行时标签，用于审计和埋点区分当前真实后端。
 */
data class LocalTextGenerationResult(
    val text: String,
    val backendLabel: String,
)

/**
 * 真本地文本生成运行时抽象。
 *
 * 用途说明：
 * - 作为 `core:data` 与具体 GGUF/llama.cpp 推理实现之间的稳定桥梁。
 * - 后续如果切换到别的端侧运行时，只需要保持这个接口语义不变即可。
 *
 * 参数说明：
 * - request：本次结构化抽取任务的标准化生成请求。
 */
interface LocalTextGenerationRuntime {
    suspend fun generate(request: LocalTextGenerationRequest): LocalTextGenerationResult
}
