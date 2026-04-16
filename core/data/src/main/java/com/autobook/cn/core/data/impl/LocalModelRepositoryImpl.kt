package com.autobook.cn.core.data.impl

import android.content.Context
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.DEFAULT_LOCAL_MODEL_ID
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.LocalModelSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 本地模型仓储实现。
 *
 * 用途说明：
 * - 管理推荐模型目录、下载状态和当前选中的模型文件。
 * - 采用应用私有目录保存模型，避免把大型 GGUF 文件直接打进 APK。
 *
 * 参数说明：
 * - context：应用上下文，用于定位模型存储目录。
 * - settingsRepository：读取和写入当前选中的模型标识。
 */
@Singleton
class LocalModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : LocalModelRepository {
    private val downloadState = MutableStateFlow(ModelDownloadState())

    /**
     * 输出当前本地模型快照。
     *
     * 参数说明：
     * - selectedModelId：当前选中的模型标识。
     * - state：下载进度和错误状态。
     */
    override val snapshot: Flow<LocalModelSnapshot> = combine(
        settingsRepository.settings
            .map { settings -> settings.selectedLocalModelId }
            .distinctUntilChanged(),
        downloadState,
    ) { selectedModelId, state ->
        /**
         * 这里只订阅“选中的模型 ID”，避免设置页里修改其它开关时也触发模型卡整块重新计算。
         *
         * 用途说明：
         * - 之前 `snapshot` 直接依赖完整 `AppSettings`，任何设置变更都会让本地模型状态重新发射。
         * - 设置页又会同时订阅 `settings` 和 `snapshot`，导致一次普通开关变更可能触发两轮 UI 状态重算。
         */
        val selectedSpec = LOCAL_MODEL_CATALOG.firstOrNull { it.id == selectedModelId }
            ?: LOCAL_MODEL_CATALOG.first { it.id == DEFAULT_LOCAL_MODEL_ID }
        val targetFile = resolveModelFile(selectedSpec)
        val installStatus = when {
            state.isDownloading -> LocalModelInstallStatus.DOWNLOADING
            targetFile.exists() -> LocalModelInstallStatus.READY
            state.lastError != null -> LocalModelInstallStatus.FAILED
            else -> LocalModelInstallStatus.NOT_DOWNLOADED
        }
        LocalModelSnapshot(
            availableModels = LOCAL_MODEL_CATALOG,
            selectedModelId = selectedSpec.id,
            selectedModelPath = targetFile.takeIf(File::exists)?.absolutePath,
            installStatus = installStatus,
            downloadProgressPercent = state.progressPercent.takeIf { installStatus == LocalModelInstallStatus.DOWNLOADING },
            statusMessage = buildStatusMessage(
                selectedSpec = selectedSpec,
                installStatus = installStatus,
                state = state,
                targetFile = targetFile,
            ),
        )
    }

    /**
     * 重新读取本地模型状态。
     *
     * 用途说明：
     * - 当外部脚本或手动下载模型文件后，允许界面主动触发一次状态刷新。
     */
    override suspend fun refresh() {
        val current = downloadState.value
        downloadState.value = current.copy(refreshTick = current.refreshTick + 1)
    }

    /**
     * 切换当前选中的模型。
     *
     * 参数说明：
     * - modelId：用户选中的模型标识。
     */
    override suspend fun selectModel(modelId: String) {
        val safeModelId = LOCAL_MODEL_CATALOG.firstOrNull { it.id == modelId }?.id ?: DEFAULT_LOCAL_MODEL_ID
        settingsRepository.setSelectedLocalModelId(safeModelId)
        refresh()
    }

    /**
     * 下载当前选中的模型到应用私有目录。
     *
     * 用途说明：
     * - 采用临时文件加落盘替换的方式，避免下载中断时留下损坏文件。
     */
    override suspend fun downloadSelectedModel() = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val spec = LOCAL_MODEL_CATALOG.firstOrNull { it.id == settings.selectedLocalModelId }
            ?: LOCAL_MODEL_CATALOG.first { it.id == DEFAULT_LOCAL_MODEL_ID }
        val targetFile = resolveModelFile(spec)
        if (targetFile.exists() && targetFile.length() > 0L) {
            downloadState.value = ModelDownloadState(
                progressPercent = 100,
                statusMessage = "模型已存在：${targetFile.name}",
            )
            return@withContext
        }

        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        downloadState.value = ModelDownloadState(
            isDownloading = true,
            progressPercent = 0,
            statusMessage = "开始下载 ${spec.displayName}",
        )

        try {
            val connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_CONNECT_TIMEOUT_MS
                readTimeout = HTTP_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.connect()
            val contentLength = connection.contentLengthLong
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) {
                            break
                        }
                        output.write(buffer, 0, count)
                        downloaded += count
                        val progress = if (contentLength > 0) {
                            ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        downloadState.value = ModelDownloadState(
                            isDownloading = true,
                            progressPercent = progress,
                            statusMessage = buildDownloadingMessage(spec.displayName, downloaded, contentLength),
                        )
                    }
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("无法把临时模型文件移动到正式目录")
            }
            downloadState.value = ModelDownloadState(
                progressPercent = 100,
                statusMessage = "模型已下载完成：${targetFile.name}",
            )
        } catch (error: Exception) {
            tempFile.delete()
            downloadState.value = ModelDownloadState(
                lastError = error.message ?: error.javaClass.simpleName,
                statusMessage = "模型下载失败：${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    /**
     * 根据模型规格解析实际落盘文件路径。
     *
     * 参数说明：
     * - spec：模型规格。
     */
    private fun resolveModelFile(spec: LocalModelSpec): File {
        return File(File(context.filesDir, LOCAL_MODEL_DIRECTORY_NAME), "${spec.id}.gguf")
    }

    /**
     * 生成适合设置页展示的本地模型状态文案。
     *
     * 参数说明：
     * - selectedSpec：当前选中的模型规格。
     * - installStatus：安装状态。
     * - state：下载过程中的临时状态。
     * - targetFile：当前模型的目标文件。
     */
    private fun buildStatusMessage(
        selectedSpec: LocalModelSpec,
        installStatus: LocalModelInstallStatus,
        state: ModelDownloadState,
        targetFile: File,
    ): String {
        return when (installStatus) {
            LocalModelInstallStatus.DOWNLOADING -> state.statusMessage
            LocalModelInstallStatus.READY -> {
                "已安装 ${selectedSpec.displayName}，文件约 ${formatBytes(targetFile.length())}，运行时建议 ${selectedSpec.runtimeHint}"
            }

            LocalModelInstallStatus.FAILED -> state.statusMessage
            LocalModelInstallStatus.NOT_DOWNLOADED -> {
                "尚未下载 ${selectedSpec.displayName}，预计 ${formatBytes(selectedSpec.estimatedSizeBytes)}，推荐运行时 ${selectedSpec.runtimeHint}"
            }
        }
    }
}

/**
 * 下载过程中的临时状态。
 *
 * 参数说明：
 * - isDownloading：当前是否正在下载。
 * - progressPercent：下载进度。
 * - statusMessage：状态说明。
 * - lastError：最近一次错误说明。
 * - refreshTick：用于触发快照重算的刷新序号。
 */
private data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val progressPercent: Int? = null,
    val statusMessage: String = "尚未下载本地模型",
    val lastError: String? = null,
    val refreshTick: Long = 0L,
)

/** 当前内置的本地模型目录名。 */
private const val LOCAL_MODEL_DIRECTORY_NAME = "local_models"

/** 下载缓冲区大小。 */
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

/** HTTP 连接超时时间。 */
private const val HTTP_CONNECT_TIMEOUT_MS = 15_000

/** HTTP 读取超时时间。 */
private const val HTTP_READ_TIMEOUT_MS = 60_000

/** 当前内置的本地模型清单。 */
private val LOCAL_MODEL_CATALOG = listOf(
    LocalModelSpec(
        id = "qwen3_1_7b_q8_0",
        displayName = "Qwen3 1.7B Q8_0",
        family = "Qwen3",
        quantization = "Q8_0",
        runtimeHint = "llama.cpp",
        estimatedSizeBytes = 1_710_000_000L,
        downloadUrl = "https://www.modelscope.cn/api/v1/models/Qwen/Qwen3-1.7B-GGUF/repo?Revision=master&FilePath=Qwen3-1.7B-Q8_0.gguf",
        isRecommended = true,
    ),
    LocalModelSpec(
        id = "qwen3_0_6b_q8_0",
        displayName = "Qwen3 0.6B Q8_0",
        family = "Qwen3",
        quantization = "Q8_0",
        runtimeHint = "llama.cpp",
        estimatedSizeBytes = 720_000_000L,
        downloadUrl = "https://www.modelscope.cn/api/v1/models/Qwen/Qwen3-0.6B-GGUF/repo?Revision=master&FilePath=Qwen3-0.6B-Q8_0.gguf",
    ),
)

/**
 * 生成下载中的状态文案。
 *
 * 参数说明：
 * - modelName：当前下载的模型名称。
 * - downloaded：已下载字节数。
 * - total：总字节数；未知时为负数。
 */
private fun buildDownloadingMessage(
    modelName: String,
    downloaded: Long,
    total: Long,
): String {
    return if (total > 0) {
        "正在下载 $modelName：${formatBytes(downloaded)} / ${formatBytes(total)}"
    } else {
        "正在下载 $modelName：${formatBytes(downloaded)}"
    }
}

/** 将字节大小格式化为更适合界面展示的字符串。 */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) {
        return "0 B"
    }
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }
    return String.format("%.1f %s", value, units[unitIndex])
}
