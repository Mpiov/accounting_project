package com.autobook.cn.core.data.impl

import com.autobook.cn.core.llm.LocalTextGenerationRequest
import com.autobook.cn.core.llm.LocalTextGenerationResult
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug 变体下的本地文本生成运行时占位实现。
 *
 * 用途说明：
 * - 避免 debug 包为了图片导入链路去装载 `llama.cpp` 与 JNI so。
 * - 统一返回一个“结构化但无候选”的 JSON，让上层 reasoner 自然走回退草稿逻辑。
 *
 * 参数说明：
 * - request：当前文本生成请求；debug 占位实现不真正执行模型推理，只保留接口语义。
 */
@Singleton
class DebugLocalTextGenerationRuntime @Inject constructor() : LocalTextGenerationRuntime {
    /**
     * 返回固定的结构化 JSON 占位结果。
     *
     * 参数说明：
     * - request：当前生成请求；保留该参数是为了和真实运行时签名一致，方便后续替换。
     */
    override suspend fun generate(request: LocalTextGenerationRequest): LocalTextGenerationResult {
        return LocalTextGenerationResult(
            text = """
                {
                  "candidates": [],
                  "message": "Debug 运行时未启用真本地模型，已交给上层回退到 OCR 草稿。",
                  "requiresManualConfirmation": true
                }
            """.trimIndent(),
            backendLabel = "debug-stub-runtime",
        )
    }
}
