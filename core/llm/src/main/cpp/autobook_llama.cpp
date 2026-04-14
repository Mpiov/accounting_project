#include <android/log.h>
#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <sstream>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"

/**
 * 说明：这里实现的是“单轮结构化抽取”场景，而不是完整聊天界面。
 * 因此 native 侧只保留模型装载、上下文准备、一次性 prompt 解码与有限 token 生成。
 */

namespace {

/** 说明：统一使用 Android log 输出，便于后续在 logcat 里追踪模型装载和推理问题。 */
constexpr const char * LOG_TAG = "AutobookLlama";

/** 说明：上下文长度先保守设为 4096，足够承载通知文本和结构化 JSON 输出。 */
constexpr int DEFAULT_CONTEXT_SIZE = 4096;

/** 说明：批处理大小保持为 512，兼顾 prompt 处理吞吐与内存占用。 */
constexpr int BATCH_SIZE = 512;

/** 说明：给 prompt 与生成阶段预留少量 headroom，避免刚好顶满上下文。 */
constexpr int CONTEXT_HEADROOM = 16;

/** 说明：为了让结构化输出更稳定，采样温度使用较低值。 */
constexpr float DEFAULT_SAMPLER_TEMP = 0.2f;

/** 说明：prompt 处理线程数保守限制在 2~4 之间，避免在移动端挤占太多前台资源。 */
constexpr int THREADS_MIN = 2;
constexpr int THREADS_MAX = 4;
constexpr int THREADS_HEADROOM = 2;

/** 说明：llama.cpp 当前模型、上下文与采样器都由单例持有，并由 Kotlin 单线程调度串行访问。 */
static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static llama_batch g_batch = {};
static common_chat_templates_ptr g_chat_templates;
static common_sampler * g_sampler = nullptr;
static bool g_backend_initialized = false;

inline void log_info(const std::string & message) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", message.c_str());
}

inline void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

/**
 * 判断字符串缓存是否已经形成合法 UTF-8。
 *
 * 参数说明：
 * - text：当前缓存中的待校验字节序列。
 */
bool is_valid_utf8(const char * text) {
    if (text == nullptr) {
        return true;
    }

    const auto * bytes = reinterpret_cast<const unsigned char *>(text);
    while (*bytes != 0x00) {
        int count = 0;
        if ((*bytes & 0x80) == 0x00) {
            count = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            count = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            count = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            count = 4;
        } else {
            return false;
        }

        ++bytes;
        for (int index = 1; index < count; ++index) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            ++bytes;
        }
    }
    return true;
}

/**
 * 释放当前已经装载的模型相关资源。
 *
 * 参数说明：
 * - 无显式参数；内部会安全判断指针是否为空。
 */
void free_loaded_resources() {
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    if (g_batch.token != nullptr) {
        llama_batch_free(g_batch);
        g_batch = {};
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

/**
 * 创建 llama 上下文。
 *
 * 参数说明：
 * - model：已经成功加载的模型指针。
 */
llama_context * create_context(llama_model * model) {
    if (model == nullptr) {
        log_error("create_context failed because model is null");
        return nullptr;
    }

    const int online_processors = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    const int thread_count = std::max(
        THREADS_MIN,
        std::min(THREADS_MAX, online_processors - THREADS_HEADROOM)
    );

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = DEFAULT_CONTEXT_SIZE;
    context_params.n_batch = BATCH_SIZE;
    context_params.n_ubatch = BATCH_SIZE;
    context_params.n_threads = thread_count;
    context_params.n_threads_batch = thread_count;

    return llama_init_from_model(model, context_params);
}

/**
 * 创建默认采样器。
 *
 * 参数说明：
 * - model：当前已加载模型，用于初始化公共采样器。
 */
common_sampler * create_sampler(const llama_model * model) {
    common_params_sampling sampling_params;
    sampling_params.temp = DEFAULT_SAMPLER_TEMP;
    return common_sampler_init(model, sampling_params);
}

/**
 * 批量解码一段 token。
 *
 * 参数说明：
 * - context：当前推理上下文。
 * - tokens：待解码 token 序列。
 * - startPosition：这段 token 在上下文中的起始位置。
 * - computeLastLogit：是否只在最后一个 token 上请求 logits，供后续生成阶段使用。
 */
bool decode_tokens(
    llama_context * context,
    const llama_tokens & tokens,
    const llama_pos startPosition,
    const bool computeLastLogit
) {
    for (int index = 0; index < static_cast<int>(tokens.size()); index += BATCH_SIZE) {
        const int currentBatchSize = std::min(static_cast<int>(tokens.size()) - index, BATCH_SIZE);
        common_batch_clear(g_batch);

        for (int batchIndex = 0; batchIndex < currentBatchSize; ++batchIndex) {
            const int tokenIndex = index + batchIndex;
            const bool wantLogit = computeLastLogit && tokenIndex == static_cast<int>(tokens.size()) - 1;
            common_batch_add(
                g_batch,
                tokens[tokenIndex],
                startPosition + tokenIndex,
                {0},
                wantLogit
            );
        }

        if (llama_decode(context, g_batch) != 0) {
            log_error("llama_decode failed while processing prompt tokens");
            return false;
        }
    }
    return true;
}

/**
 * 根据 chat template 格式化单条消息，并把该消息追加到历史里。
 *
 * 参数说明：
 * - history：当前会话中的历史消息。
 * - role：当前消息角色，例如 system 或 user。
 * - content：当前消息正文。
 * - addAssistantPrompt：是否在 user 消息后追加 assistant generation prompt。
 */
std::string format_message(
    std::vector<common_chat_msg> & history,
    const std::string & role,
    const std::string & content,
    const bool addAssistantPrompt
) {
    common_chat_msg message;
    message.role = role;
    message.content = content;

    const std::string formatted = common_chat_format_single(
        g_chat_templates.get(),
        history,
        message,
        addAssistantPrompt,
        false
    );
    history.push_back(message);
    return formatted;
}

/**
 * 解码 system 或 user 阶段的 prompt。
 *
 * 参数说明：
 * - history：聊天历史，用于 chat template 格式化。
 * - role：当前阶段角色。
 * - content：当前阶段正文。
 * - position：当前上下文写入位置，会在成功后向前推进。
 * - computeLastLogit：是否为最后一个 prompt 片段保留 logits。
 */
bool run_prompt_stage(
    std::vector<common_chat_msg> & history,
    const std::string & role,
    const std::string & content,
    llama_pos & position,
    const bool computeLastLogit
) {
    if (content.empty()) {
        return true;
    }

    const bool hasExplicitTemplate = common_chat_templates_was_explicit(g_chat_templates.get());
    std::string formatted = content;
    if (hasExplicitTemplate) {
        formatted = format_message(
            history,
            role,
            content,
            role == "user"
        );
    }

    const auto tokens = common_tokenize(
        g_context,
        formatted,
        hasExplicitTemplate,
        hasExplicitTemplate
    );
    if (position + static_cast<llama_pos>(tokens.size()) >= DEFAULT_CONTEXT_SIZE - CONTEXT_HEADROOM) {
        log_error("prompt tokens exceed available context window");
        return false;
    }
    if (!decode_tokens(g_context, tokens, position, computeLastLogit)) {
        return false;
    }

    position += static_cast<llama_pos>(tokens.size());
    return true;
}

/**
 * 生成单轮结构化结果。
 *
 * 参数说明：
 * - maxTokens：最大生成 token 数。
 * - startPosition：当前 prompt 写入完成后的上下文位置。
 */
std::string generate_response(const int maxTokens, llama_pos startPosition) {
    std::string output;
    std::string utf8Cache;

    for (int index = 0; index < maxTokens; ++index) {
        if (startPosition >= DEFAULT_CONTEXT_SIZE - CONTEXT_HEADROOM) {
            log_error("generation stopped because context window is full");
            break;
        }

        const llama_token nextToken = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, nextToken, true);

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), nextToken)) {
            break;
        }

        utf8Cache += common_token_to_piece(g_context, nextToken);
        if (is_valid_utf8(utf8Cache.c_str())) {
            output += utf8Cache;
            utf8Cache.clear();
        }

        common_batch_clear(g_batch);
        common_batch_add(g_batch, nextToken, startPosition, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            log_error("llama_decode failed while generating response tokens");
            break;
        }

        ++startPosition;
    }

    return output;
}

}  // namespace

extern "C"
JNIEXPORT void JNICALL
Java_com_autobook_cn_core_llm_LlamaCppRuntime_init(JNIEnv * env, jobject /* this */, jstring nativeLibDir) {
    if (g_backend_initialized) {
        return;
    }

    llama_log_set([](enum ggml_log_level, const char * text, void *) {
        if (text != nullptr) {
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", text);
        }
    }, nullptr);

    llama_backend_init();
    const char * pathToBackend = env->GetStringUTFChars(nativeLibDir, nullptr);
    // 说明：Android 上 so 可能直接打包在 APK 内而不是可枚举目录里，这里统一走“已链接 backend 的注册 + 可选兜底加载”。
    ggml_backend_load_all();
    env->ReleaseStringUTFChars(nativeLibDir, pathToBackend);
    g_backend_initialized = true;
    {
        std::ostringstream message;
        message << "llama backend initialized, backend_count=" << ggml_backend_reg_count();
        log_info(message.str());
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_autobook_cn_core_llm_LlamaCppRuntime_load(JNIEnv * env, jobject /* this */, jstring modelPath) {
    free_loaded_resources();

    llama_model_params modelParams = llama_model_default_params();
    const char * rawModelPath = env->GetStringUTFChars(modelPath, nullptr);
    auto * loadedModel = llama_model_load_from_file(rawModelPath, modelParams);
    env->ReleaseStringUTFChars(modelPath, rawModelPath);
    if (loadedModel == nullptr) {
        log_error("failed to load gguf model");
        return 1;
    }

    g_model = loadedModel;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_autobook_cn_core_llm_LlamaCppRuntime_prepare(JNIEnv * /* env */, jobject /* this */) {
    if (g_model == nullptr) {
        log_error("prepare failed because model is not loaded");
        return 1;
    }

    g_context = create_context(g_model);
    if (g_context == nullptr) {
        log_error("prepare failed because context creation returned null");
        return 2;
    }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = create_sampler(g_model);
    if (g_sampler == nullptr) {
        log_error("prepare failed because sampler creation returned null");
        return 3;
    }

    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_autobook_cn_core_llm_LlamaCppRuntime_infer(
    JNIEnv * env,
    jobject /* this */,
    jstring systemPrompt,
    jstring userPrompt,
    jint maxTokens
) {
    if (g_model == nullptr || g_context == nullptr || g_sampler == nullptr) {
        log_error("infer failed because model/context/sampler is not ready");
        return nullptr;
    }

    const char * rawSystemPrompt = env->GetStringUTFChars(systemPrompt, nullptr);
    const char * rawUserPrompt = env->GetStringUTFChars(userPrompt, nullptr);

    std::string systemPromptText(rawSystemPrompt == nullptr ? "" : rawSystemPrompt);
    std::string userPromptText(rawUserPrompt == nullptr ? "" : rawUserPrompt);

    env->ReleaseStringUTFChars(systemPrompt, rawSystemPrompt);
    env->ReleaseStringUTFChars(userPrompt, rawUserPrompt);

    llama_memory_clear(llama_get_memory(g_context), false);
    common_sampler_reset(g_sampler);

    std::vector<common_chat_msg> history;
    llama_pos currentPosition = 0;

    if (!run_prompt_stage(history, "system", systemPromptText, currentPosition, false)) {
        return nullptr;
    }
    if (!run_prompt_stage(history, "user", userPromptText, currentPosition, true)) {
        return nullptr;
    }

    const std::string response = generate_response(maxTokens, currentPosition);
    if (response.empty()) {
        log_error("infer finished but response is empty");
        return nullptr;
    }

    return env->NewStringUTF(response.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autobook_cn_core_llm_LlamaCppRuntime_unload(JNIEnv * /* env */, jobject /* this */) {
    free_loaded_resources();
}
