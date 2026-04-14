package com.autobook.cn.core.llm

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 llama.cpp JNI 的真本地运行时实现。
 *
 * 用途说明：
 * - 负责把上层的一次性结构化抽取请求映射成 “初始化后端 -> 按需加载模型 -> 执行一轮生成” 的原生调用。
 * - 内部通过单线程协程串行访问 native 状态，避免多并发请求把同一份 llama 上下文打坏。
 *
 * 参数说明：
 * - context：应用上下文，用于定位 native so 所在目录。
 */
class LlamaCppRuntime(
    private val context: Context,
) : LocalTextGenerationRuntime {
    /** 说明：llama.cpp 的模型与上下文都是可变全局状态，必须强制单线程串行访问。 */
    private val runtimeDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** 说明：记录当前已经装载到 native 侧的模型路径，避免重复加载大模型。 */
    private var loadedModelPath: String? = null

    /** 说明：只在首次调用时初始化 backend 与 JNI，避免重复 `System.loadLibrary`。 */
    private var initialized = false

    /** 说明：显式记录 JNI 主库是否已经成功装载，避免外部方法在未装载时直接触发 `No implementation found`。 */
    private var nativeRuntimeLibraryLoaded = false

    /**
     * 执行一次完整的端侧结构化生成。
     *
     * 参数说明：
     * - request：包含模型路径、system prompt、user prompt 与 token 上限的标准化请求。
     */
    override suspend fun generate(request: LocalTextGenerationRequest): LocalTextGenerationResult =
        withContext(runtimeDispatcher) {
            require(request.modelPath.isNotBlank()) { "模型路径不能为空" }
            require(request.systemPrompt.isNotBlank()) { "system prompt 不能为空" }
            require(request.userPrompt.isNotBlank()) { "user prompt 不能为空" }
            require(request.maxTokens > 0) { "maxTokens 必须大于 0" }

            ensureInitialized()
            ensureModelLoaded(request.modelPath)

            val generatedText = infer(
                systemPrompt = request.systemPrompt,
                userPrompt = request.userPrompt,
                maxTokens = request.maxTokens,
            )?.trim().orEmpty()
            check(generatedText.isNotBlank()) { "llama.cpp 返回了空响应" }

            LocalTextGenerationResult(
                text = generatedText,
                backendLabel = "llama.cpp",
            )
        }

    /**
     * 初始化 native backend。
     *
     * 参数说明：
     * - 无显式参数；内部使用应用 so 目录加载 CPU backend 变体。
     */
    private fun ensureInitialized() {
        if (initialized) {
            return
        }

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        loadBundledRuntimeLibraries(nativeLibDir)
        ensurePrimaryRuntimeLibraryLoaded(nativeLibDir)
        init(nativeLibDir)
        initialized = true
    }

    /**
     * 主动按依赖顺序加载随 APK 打包的 llama.cpp 运行时动态库。
     *
     * 参数说明：
     * - nativeLibDir：当前 ABI 对应的 so 目录，用于枚举 `ggml`、`llama` 与 CPU backend 变体。
     *
     * 说明：
     * - 真机上 `ggml_backend_load_all_from_path()` 依赖 backend so 已经可被当前进程解析。
     * - 这里先把基础库、OpenMP 库和 CPU backend 变体全部 `System.load` 进来，
     *   可以减少 Android 动态加载器因为依赖链未准备好而导致 backend 注册失败的概率。
     */
    private fun loadBundledRuntimeLibraries(nativeLibDir: String) {
        val libraryDirectory = File(nativeLibDir)
        val bundledLibraries = libraryDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "so" }
            .orEmpty()
        if (bundledLibraries.isEmpty()) {
            Log.w(TAG, "nativeLibraryDir 中未枚举到任何 so，后续将退回到显式主库装载：$nativeLibDir")
            return
        }

        val loadOrder = buildList {
            bundledLibraries.firstOrNull { it.name == "libomp.so" }?.let(::add)
            bundledLibraries.firstOrNull { it.name == "libggml-base.so" }?.let(::add)
            bundledLibraries.firstOrNull { it.name == "libggml.so" }?.let(::add)
            bundledLibraries.firstOrNull { it.name == "libllama.so" }?.let(::add)
            bundledLibraries
                .filter { it.name.startsWith("libggml-cpu-") }
                .sortedBy { file -> file.name }
                .forEach(::add)
            bundledLibraries.firstOrNull { it.name == "lib$NATIVE_LIBRARY_NAME.so" }?.let(::add)
        }

        loadOrder.distinctBy { file -> file.absolutePath }.forEach { libraryFile ->
            // 同一路径的 so 在同一个 ClassLoader 中重复 load 会抛错，这里允许“已经加载过”静默跳过。
            runCatching { System.load(libraryFile.absolutePath) }
                .onSuccess {
                    if (libraryFile.name == "lib$NATIVE_LIBRARY_NAME.so") {
                        nativeRuntimeLibraryLoaded = true
                    }
                }
                .onFailure { error ->
                    val message = error.message.orEmpty()
                    if ("already loaded" !in message) {
                        throw error
                    }
                    if (libraryFile.name == "lib$NATIVE_LIBRARY_NAME.so") {
                        nativeRuntimeLibraryLoaded = true
                    }
                }
        }
    }

    /**
     * 显式装载 JNI 主库。
     *
     * 参数说明：
     * - nativeLibDir：当前 ABI 对应的 so 目录；优先按绝对路径装载，失败后再回退到 `System.loadLibrary`。
     *
     * 说明：
     * - 之前仅依赖目录枚举顺带装载主库，在部分设备上会出现主库其实没装进 ClassLoader，
     *   进而在首次调用 external 方法时直接抛出 `No implementation found`。
     */
    private fun ensurePrimaryRuntimeLibraryLoaded(nativeLibDir: String) {
        if (nativeRuntimeLibraryLoaded) {
            return
        }

        val runtimeLibraryFile = File(nativeLibDir, "lib$NATIVE_LIBRARY_NAME.so")
        val loadResult = runCatching {
            when {
                runtimeLibraryFile.exists() -> {
                    System.load(runtimeLibraryFile.absolutePath)
                    "path:${runtimeLibraryFile.absolutePath}"
                }

                else -> {
                    System.loadLibrary(NATIVE_LIBRARY_NAME)
                    "library:$NATIVE_LIBRARY_NAME"
                }
            }
        }

        loadResult
            .onSuccess { source ->
                nativeRuntimeLibraryLoaded = true
                Log.i(TAG, "已显式装载 JNI 主库，来源=$source")
            }
            .onFailure { error ->
                val message = error.message ?: error.javaClass.simpleName
                Log.e(TAG, "装载 JNI 主库失败：$message")
                throw error
            }
    }

    /**
     * 按需加载或切换模型。
     *
     * 参数说明：
     * - modelPath：目标 GGUF 模型的绝对路径。
     */
    private fun ensureModelLoaded(modelPath: String) {
        if (loadedModelPath == modelPath) {
            return
        }

        if (loadedModelPath != null) {
            // 说明：切换模型前必须先把旧模型与上下文卸载干净，避免 native 持有旧指针。
            unload()
            loadedModelPath = null
        }

        check(load(modelPath) == 0) { "加载模型失败：$modelPath" }
        check(prepare() == 0) { "准备 llama.cpp 上下文失败：$modelPath" }
        loadedModelPath = modelPath
    }

    /**
     * JNI 初始化入口。
     *
     * 参数说明：
     * - nativeLibDir：应用 native so 目录，用于加载 ggml backend 变体。
     */
    private external fun init(nativeLibDir: String)

    /**
     * JNI 模型加载入口。
     *
     * 参数说明：
     * - modelPath：GGUF 模型文件的绝对路径。
     *
     * 返回值说明：
     * - `0` 代表成功，非 `0` 代表 native 侧加载失败。
     */
    private external fun load(modelPath: String): Int

    /**
     * JNI 上下文准备入口。
     *
     * 返回值说明：
     * - `0` 代表成功，非 `0` 代表 native 侧初始化上下文失败。
     */
    private external fun prepare(): Int

    /**
     * JNI 一次性生成入口。
     *
     * 参数说明：
     * - systemPrompt：系统提示词。
     * - userPrompt：用户提示词。
     * - maxTokens：最大生成 token 数。
     */
    private external fun infer(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
    ): String?

    /**
     * JNI 卸载当前模型入口。
     *
     * 参数说明：
     * - 无显式参数；native 侧会释放模型、上下文、sampler 与 batch。
     */
    private external fun unload()

    private companion object {
        /** 说明：统一 Kotlin 侧日志标签，方便和 native 的 `AutobookLlama` 日志一起检索。 */
        const val TAG = "AutobookLlamaKt"

        /** 说明：与 `CMakeLists.txt` 里生成的 `libautobook-llama.so` 保持一致。 */
        const val NATIVE_LIBRARY_NAME = "autobook-llama"
    }
}
