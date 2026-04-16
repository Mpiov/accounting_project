package com.autobook.cn.core.data.impl

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autobook.cn.core.data.CaptureCoordinator
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.NotificationTraceRepository
import com.autobook.cn.core.data.NotificationIngestor
import com.autobook.cn.core.data.OcrEngine
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toDomain
import com.autobook.cn.core.data.mapper.toEntity
import com.autobook.cn.core.data.parser.enrichForProcessing
import com.autobook.cn.core.data.pipeline.TransactionProcessingPipeline
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.CaptureSessionState
import com.autobook.cn.core.model.DEFAULT_LOCAL_MODEL_ID
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DecimalFormat
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "autobook_settings")

/** 鍩轰簬 Room 鐨勮处鏈粨鍌ㄥ疄鐜般€?*/
@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
) : LedgerRepository {
    override fun observeEntries(): Flow<List<LedgerEntry>> = ledgerEntryDao.observeAll().map { entries ->
        entries.map { it.toDomain() }
    }

    override suspend fun add(entry: LedgerEntry): Long = ledgerEntryDao.insert(entry.toEntity())

    override suspend fun update(entry: LedgerEntry) {
        ledgerEntryDao.update(entry.toEntity())
    }

    override suspend fun delete(id: Long) {
        ledgerEntryDao.deleteById(id)
    }

    override suspend fun getById(id: Long): LedgerEntry? = ledgerEntryDao.getById(id)?.toDomain()

    override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? {
        /**
         * 按通知自动记账的账本判重规则查找疑似重复记录。
         *
         * 参数说明：
         * - candidate：当前待判重的交易候选。
         */
        // 这里只看“金额 + 方向 + 短时间窗口”，不再要求商户命中。
        // 这样支付宝通知与银行卡动账提醒即使商户提取不同，也仍有机会被识别为同一笔交易。
        // orderId 如存在，继续作为更强的辅助信号参与 SQL 判重。
        val duplicate = ledgerEntryDao.findPotentialDuplicate(
            orderId = candidate.orderId,
            amount = candidate.amount,
            direction = candidate.direction.name,
            windowStart = candidate.timestamp - DUPLICATE_WINDOW_MS,
            windowEnd = candidate.timestamp + DUPLICATE_WINDOW_MS,
        )
        return duplicate?.toDomain()
    }

    /**
     * 閽堝鍥剧墖瀵煎叆鍘嗗彶璁板綍鎵ц瀹芥澗鍒ら噸銆?     *
     * 鐢ㄩ€旇鏄庯細
     * - 鎴浘閲岀殑浜ゆ槗寰堝彲鑳芥棭宸插彂鐢燂紝涓嶈兘鍐嶄緷璧栧疄鏃舵椂闂寸獥銆?     * - 褰撳墠鍥剧墖/鎴浘閾捐矾鍙湪鈥滃悓鍒嗛挓 + 鍚岄噾棰?+ 鍚屾柟鍚戔€濅竴鑷存椂鎵嶈涓洪噸澶嶃€?     * - 杩欓噷灞炰簬寮哄垽閲嶏紝鍛戒腑鍚庣洿鎺ヨ涓洪噸澶嶅€欓€夈€?     *
     * 鍙傛暟璇存槑锛?     * - timestamp锛氬€欓€変氦鏄撴椂闂达紱涓虹┖鏃剁洿鎺ヨ烦杩囧巻鍙插垽閲嶃€?     * - amount锛氬€欓€夐噾棰濄€?     * - direction锛氬€欓€夋柟鍚戙€?     */
    override suspend fun findPotentialImageImportDuplicate(
        timestamp: Long?,
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        if (timestamp == null) {
            return null
        }
        // 账单截图通常只能可靠到分钟，因此这里先把候选时间归一化成分钟窗口再查库。
        val minuteWindow = timestamp.toMinuteWindow()
        val duplicate = ledgerEntryDao.findPotentialImageImportDuplicate(
            windowStart = minuteWindow.startInclusive,
            windowEnd = minuteWindow.endInclusive,
            amount = amount,
            direction = direction.name,
        )
        return duplicate?.toDomain()
    }

    /**
     * 閽堝鈥滃€欓€夋病鏈夎瘑鍒嚭鏃堕棿锛屼絾璐︽湰宸插瓨鍦ㄥ悓閲戦鍚屾柟鍚戣褰曗€濈殑鍦烘櫙鍋氭ā绯婇噸澶嶆帰娴嬨€?     *
     * 鐢ㄩ€旇鏄庯細
     * - 褰撳墠涓嶆妸杩欑被鍛戒腑褰撴垚寮洪噸澶嶏紝鍥犱负缂哄皯鍊欓€変晶鏃堕棿璇佹嵁銆?     * - 涓婂眰濡傛灉鍙戠幇鍊欓€夋湰韬槸涓珮缃俊搴︼紝鍙互鎹鎶婂畠闄嶇骇鎴愬緟纭锛岃€屼笉鏄洿鎺ヨ繃婊ゃ€?     *
     * 鍙傛暟璇存槑锛?     * - amount锛氬€欓€夐噾棰濄€?     * - direction锛氬€欓€夋柟鍚戙€?     */
    override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        val duplicate = ledgerEntryDao.findPotentialImageImportDuplicateWithoutTimestamp(
            amount = amount,
            direction = direction.name,
        )
        return duplicate?.toDomain()
    }

    override suspend fun exportCsv(): String {
        val rows = observeEntries().first()
        return buildString {
            // 瀵煎嚭鏃朵繚鐣?trace 瀛楁锛屼究浜庡悗缁帓鏌ヨ褰曟潵婧愩€?
            appendLine("id,timestamp,direction,amount,merchant,channel,category,orderId,note,traceSource")
            rows.forEach { entry ->
                appendLine(
                    listOf(
                        entry.id,
                        entry.timestamp,
                        entry.direction.name,
                        entry.amount,
                        csvEscape(entry.merchant),
                        csvEscape(entry.channel),
                        csvEscape(entry.category),
                        csvEscape(entry.orderId),
                        csvEscape(entry.note),
                        csvEscape(entry.traceSource),
                    ).joinToString(","),
                )
            }
        }
    }
}

/** 鍩轰簬 Room 鐨勫緟纭浠撳偍瀹炵幇銆?*/
@Singleton
class PendingRepositoryImpl @Inject constructor(
    private val pendingEntryDao: PendingEntryDao,
) : PendingRepository {
    override fun observeEntries(): Flow<List<PendingEntry>> = pendingEntryDao.observeAll().map { entries ->
        entries.map { it.toDomain() }
    }

    override suspend fun add(entry: PendingEntry): Long = pendingEntryDao.insert(entry.toEntity())

    override suspend fun update(entry: PendingEntry) {
        pendingEntryDao.update(entry.toEntity())
    }

    override suspend fun getById(id: Long): PendingEntry? = pendingEntryDao.getById(id)?.toDomain()

    override suspend fun confirm(id: Long): PendingEntry? {
        // 纭鏃剁洿鎺ュ垹闄ゅ緟纭椤癸紝骞舵妸鏈€鍚庣姸鎬佷氦缁欎笂灞傝浆鎴愯处鏈褰曘€?
        val item = getById(id) ?: return null
        pendingEntryDao.deleteById(id)
        return item
    }

    override suspend fun ignore(id: Long) {
        pendingEntryDao.deleteById(id)
    }

    override suspend fun markFalsePositive(id: Long) {
        val item = getById(id) ?: return
        pendingEntryDao.update(item.copy(isFalsePositive = true).toEntity())
    }
}

/** 鍩轰簬 Room 鐨勯€氱煡鍘熸枃鏌ヨ瀹炵幇銆?*/
@Singleton
class NotificationTraceRepositoryImpl @Inject constructor(
    private val notificationEventDao: NotificationEventDao,
) : NotificationTraceRepository {
    override fun observeRawTextById(): Flow<Map<Long, String>> = notificationEventDao.observeAll().map { events ->
        events.mapNotNull { event ->
            val rawText = listOfNotNull(
                event.title?.trim()?.takeIf { it.isNotEmpty() },
                event.text?.trim()?.takeIf { it.isNotEmpty() },
            ).distinct().takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
            rawText?.let { event.id to it }
        }.toMap()
    }
}

/** 鍩轰簬 DataStore 鐨勮缃粨鍌ㄥ疄鐜般€?*/
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    /** 鎵€鏈夎缃敭缁熶竴鏀跺彛锛岄伩鍏嶆暎钀藉瓧绗︿覆甯搁噺銆?*/
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val autoAccountingEnabled = booleanPreferencesKey("auto_accounting_enabled")
        val cloudAnalysisEnabled = booleanPreferencesKey("cloud_analysis_enabled")
        /** 鏈湴璇箟澧炲己锛圠LM 棰勭暀锛夊紑鍏崇殑鎸佷箙鍖栭敭锛屾殏鏃剁敤浜庡惎鍙戝紡閫氳矾锛屽悗缁浛鎹㈢湡鏈?LLM 鏃跺彲鐩存帴澶嶇敤銆?*/
        val localLlmConfidenceBoostEnabled = booleanPreferencesKey("local_llm_confidence_boost_enabled")
        /** 褰撳墠閫夋嫨鐨勬湰鍦版ā鍨嬫爣璇嗭紝榛樿鎸囧悜鎺ㄨ崘鐨?Qwen3 1.7B 閲忓寲妯″瀷銆?*/
        val selectedLocalModelId = stringPreferencesKey("selected_local_model_id")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val ocrEnabled = booleanPreferencesKey("ocr_enabled")
        val notificationRetentionDays = intPreferencesKey("notification_retention_days")
        val screenshotRetentionDays = intPreferencesKey("screenshot_retention_days")
        /** 鎸夋湀浠戒繚瀛橀绠楃殑杞婚噺瀛楃涓诧紝鏍煎紡涓?`yyyy-MM=1234.5;yyyy-MM=2500`銆?*/
        val monthlyBudgets = stringPreferencesKey("monthly_budgets")
        val enabledSources = stringSetPreferencesKey("enabled_sources")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        // 璇诲彇鏉ユ簮鍒楄〃鏃惰繃婊ゆ湭鐭ユ灇涓惧€硷紝閬垮厤鏃ф暟鎹鑷村穿婧冦€?
        AppSettings(
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
            autoAccountingEnabled = preferences[Keys.autoAccountingEnabled] ?: true,
            // 浜戠鍒嗘瀽鏀逛负榛樿寮€鍚紱鏃х敤鎴疯嫢宸插啓鍏ュ亸濂藉€硷紝浠嶄互鎸佷箙鍖栭厤缃负鍑嗐€?            cloudAnalysisEnabled = preferences[Keys.cloudAnalysisEnabled] ?: true,
            localLlmConfidenceBoostEnabled = preferences[Keys.localLlmConfidenceBoostEnabled] ?: false,
            selectedLocalModelId = preferences[Keys.selectedLocalModelId] ?: DEFAULT_LOCAL_MODEL_ID,
            captureEnabled = preferences[Keys.captureEnabled] ?: false,
            ocrEnabled = preferences[Keys.ocrEnabled] ?: false,
            notificationRetentionDays = preferences[Keys.notificationRetentionDays] ?: 30,
            screenshotRetentionDays = preferences[Keys.screenshotRetentionDays] ?: 7,
            monthlyBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()),
            enabledSources = resolveEnabledSources(preferences[Keys.enabledSources]),
        )
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    override suspend fun setAutoAccountingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoAccountingEnabled] = enabled }
    }

    override suspend fun setCloudAnalysisEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.cloudAnalysisEnabled] = enabled }
    }

    /** 鍐欏叆鏈湴璇箟澧炲己寮€鍏筹紝opened 鏃跺彲瑙﹀彂鍗犱綅璇箟鍒嗘瀽閾捐矾骞朵负鍚庣画鐪?LLM 淇濇寔涓€鑷存帴鍙ｃ€?*/
    override suspend fun setLocalLlmConfidenceBoostEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.localLlmConfidenceBoostEnabled] = enabled }
    }

    /** 鍐欏叆褰撳墠閫変腑鐨勬湰鍦版ā鍨嬫爣璇嗭紝渚涗笅杞藉櫒鍜屾帹鐞嗗紩鎿庣粺涓€璇诲彇銆?*/
    override suspend fun setSelectedLocalModelId(modelId: String) {
        context.settingsDataStore.edit { it[Keys.selectedLocalModelId] = modelId }
    }

    override suspend fun setCaptureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.captureEnabled] = enabled }
    }

    override suspend fun setOcrEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ocrEnabled] = enabled }
    }

    override suspend fun setNotificationRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.notificationRetentionDays] = days.coerceIn(1, 90) }
    }

    override suspend fun setScreenshotRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.screenshotRetentionDays] = days.coerceIn(1, 30) }
    }

    /** 棰勭畻閲囩敤杞婚噺瀛楃涓插瓨鍌紝閬垮厤涓轰簡鐣岄潰澧炲己鍘诲紩鍏ユ柊鐨勬暟鎹簱琛ㄣ€?*/
    override suspend fun setMonthlyBudget(monthKey: String, amount: Double) {
        context.settingsDataStore.edit { preferences ->
            val existingBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()).toMutableMap()
            existingBudgets[monthKey] = amount.coerceAtLeast(0.0)
            preferences[Keys.monthlyBudgets] = serializeMonthlyBudgets(existingBudgets)
        }
    }

    /** 娓呴櫎鏌愪釜鏈堥绠楁椂鍙Щ闄ゅ搴旈敭锛屼笉褰卞搷鍏跺畠鏈堜唤銆?*/
    override suspend fun clearMonthlyBudget(monthKey: String) {
        context.settingsDataStore.edit { preferences ->
            val existingBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()).toMutableMap()
            existingBudgets.remove(monthKey)
            preferences[Keys.monthlyBudgets] = serializeMonthlyBudgets(existingBudgets)
        }
    }

    override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) {
        setSourcesEnabled(setOf(source), enabled)
    }

    /**
     * 鎵归噺鍒囨崲閫氱煡鏉ユ簮鏃剁粺涓€璧板崟娆?DataStore 缂栬緫锛岄伩鍏嶅娆″啓鍥炰箣闂存毚闇蹭腑闂存€併€?     *
     * 鍙傛暟璇存槑锛?     * - sources锛氭湰娆￠渶瑕佺粺涓€鍚仠鐨勫簳灞傛潵婧愰泦鍚堛€?     * - enabled锛氱洰鏍囩姸鎬侊紱`true` 琛ㄧず鍔犲叆鍚敤闆嗗悎锛宍false` 琛ㄧず绉诲嚭鍚敤闆嗗悎銆?     */
    override suspend fun setSourcesEnabled(
        sources: Set<NotificationSourceApp>,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.enabledSources] = applySourceEnabledChange(
                currentSourceNames = preferences[Keys.enabledSources],
                sources = sources,
                enabled = enabled,
            )
        }
    }
}

/** 閫氱煡鍏ュ簱涓庡鐞嗘祦姘寸嚎鐨勬€诲崗璋冨櫒銆?*/
@Singleton
class NotificationIngestorImpl @Inject constructor(
    private val notificationEventDao: NotificationEventDao,
    private val pipeline: TransactionProcessingPipeline,
    private val settingsRepository: SettingsRepository,
) : NotificationIngestor {
    override suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome> {
        val enriched = event.enrichForProcessing()
        val id = notificationEventDao.insert(enriched.toEntity())
        val persisted = enriched.copy(id = id)
        val settings = settingsRepository.settings.first()
        // 自动记账总开关关闭时，通知主链路在入库后立即停止，不再继续解析或进入待确认。
        if (!settings.autoAccountingEnabled) {
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.FILTERED,
                status = NotificationProcessStatus.FILTERED_OUT,
                message = "Auto accounting disabled by user settings",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

        // 用户已经关闭的来源在这里直接短路，不再继续进入后续流水线。
        // 未知来源不能在解析前被用户来源开关提前拦截；应先进入规则层，由正文语义决定是否过滤。
        if (persisted.sourceApp !in settings.enabledSources) {
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.FILTERED,
                status = NotificationProcessStatus.FILTERED_OUT,
                message = "Source disabled by user settings",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

        // 通知级精确哈希去重已移除；通知入库后直接进入解析与账本级判重流程。
        notificationEventDao.updateProcessingState(
            id = id,
            processStatus = NotificationProcessStatus.PROCESSING.name,
            lastError = null,
            retryCount = persisted.retryCount,
            screenshotRef = persisted.screenshotRef,
        )

        return when (val result = pipeline.process(notificationId = id, event = persisted)) {
            is OperationResult.Success -> {
                notificationEventDao.updateProcessingState(
                    id = id,
                    processStatus = result.data.status.name,
                    lastError = result.data.message,
                    retryCount = persisted.retryCount,
                    screenshotRef = result.data.screenshotRef ?: persisted.screenshotRef,
                )
                result
            }

            is OperationResult.RetryableFailure -> {
                notificationEventDao.updateProcessingState(
                    id = id,
                    processStatus = NotificationProcessStatus.FAILED_RETRYABLE.name,
                    lastError = sanitize(result.message),
                    retryCount = persisted.retryCount + 1,
                    screenshotRef = persisted.screenshotRef,
                )
                result
            }

            is OperationResult.TerminalFailure -> {
                notificationEventDao.updateProcessingState(
                    id = id,
                    processStatus = NotificationProcessStatus.FAILED_TERMINAL.name,
                    lastError = sanitize(result.message),
                    retryCount = persisted.retryCount,
                    screenshotRef = persisted.screenshotRef,
                )
                result
            }
        }
    }

    /** 杩囨护銆佸幓閲嶇瓑鎴愬姛鍒嗘敮鍏辩敤鐨勭姸鎬佸洖鍐欓€昏緫銆?*/
    private suspend fun completeSuccessfully(
        notificationId: Long,
        destination: ProcessDestination,
        status: NotificationProcessStatus,
        message: String,
        retryCount: Int,
        screenshotRef: String?,
    ): OperationResult.Success<NotificationProcessOutcome> {
        notificationEventDao.updateProcessingState(
            id = notificationId,
            processStatus = status.name,
            lastError = message,
            retryCount = retryCount,
            screenshotRef = screenshotRef,
        )
        return OperationResult.Success(
            NotificationProcessOutcome(
                notificationId = notificationId,
                destination = destination,
                status = status,
                screenshotRef = screenshotRef,
                message = message,
            ),
        )
    }
}

/** MediaProjection 灏氭湭鎺ュ叆鍓嶇殑鎴浘鍗忚皟鍣ㄥ崰浣嶅疄鐜般€?*/
@Singleton
class PlaceholderCaptureCoordinator @Inject constructor() : CaptureCoordinator {
    override suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession> {
        return OperationResult.Success(
            CaptureSession(
                sessionId = UUID.randomUUID().toString(),
                state = CaptureSessionState.REQUESTED,
                reason = reason,
            ),
        )
    }

    override suspend fun capture(sessionId: String): OperationResult<String> {
        return OperationResult.RetryableFailure("Capture session $sessionId is not connected to MediaProjection yet")
    }

    override suspend fun cancel(sessionId: String) = Unit

    override suspend fun reportFailure(sessionId: String, message: String) = Unit
}

/** OCR 灏氭湭鎺ュ叆鍓嶇殑鍗犱綅瀹炵幇銆?*/
@Singleton
class FakeOcrEngine @Inject constructor() : OcrEngine {
    override suspend fun extractText(imageRef: String): OperationResult<String> {
        return OperationResult.RetryableFailure("OCR engine is not enabled for $imageRef")
    }
}

/** 棣栨瀹夎鏃堕粯璁ゅ紑鍚殑閫氱煡鏉ユ簮銆?*/
private val DEFAULT_SOURCES = setOf(
    NotificationSourceApp.WECHAT,
    NotificationSourceApp.ALIPAY,
    NotificationSourceApp.BANK_SMS,
    NotificationSourceApp.UNKNOWN,
)

/**
 * 通知自动记账链路使用的短时间判重窗口。
 *
 * 用途说明：
 * - 当前窗口为前后 30 秒，用来识别同一笔交易先后触发的支付宝通知与银行卡动账提醒。
 * - 图片导入链路不复用这套规则，而是继续使用分钟级时间比较。
 */
private const val DUPLICATE_WINDOW_MS = 30 * 1000L

/**
 * 鍒嗛挓绾ф椂闂寸獥鍙ｃ€? *
 * 鍙傛暟璇存槑锛? * - startInclusive锛氳鍒嗛挓璧峰姣銆? * - endInclusive锛氳鍒嗛挓缁撴潫姣銆? */
private data class MinuteWindow(
    val startInclusive: Long,
    val endInclusive: Long,
)

/**
 * 鎶婁换鎰忔绉掓椂闂存埑褰掍竴鍖栨垚鎵€灞炲垎閽熺殑闂尯闂淬€? *
 * 鍙傛暟璇存槑锛? * - 鎺ユ敹鑰咃細鍘熷姣鏃堕棿鎴炽€? */
private fun Long.toMinuteWindow(): MinuteWindow {
    val startInclusive = this - (this % ONE_MINUTE_MILLIS)
    val endInclusive = startInclusive + ONE_MINUTE_MILLIS - 1
    return MinuteWindow(
        startInclusive = startInclusive,
        endInclusive = endInclusive,
    )
}

/**
 * 瑙ｆ瀽鎸佷箙鍖栧悗鐨勯€氱煡鏉ユ簮闆嗗悎銆? *
 * 鐢ㄩ€旇鏄庯細
 * - 鍙湁鍦ㄧ敤鎴蜂粠鏈繚瀛樿繃璇ュ瓧娈垫椂锛屾墠鍥為€€鍒伴粯璁ゅ叏寮€銆? * - 濡傛灉鐢ㄦ埛鏄惧紡鎶婃墍鏈夋潵婧愰兘鍏虫帀锛屾寔涔呭眰淇濆瓨鐨勭┖闆嗗悎蹇呴』琚師鏍疯繕鍘燂紝涓嶈兘鍐嶅伔鍋锋仮澶嶆垚榛樿鍊笺€? *
 * 鍙傛暟璇存槑锛? * - sourceNames锛欴ataStore 涓繚瀛樼殑鏉ユ簮鍚嶇О闆嗗悎锛沗null` 琛ㄧず灏氭湭鍐欏叆杩囪瀛楁銆? */
internal fun resolveEnabledSources(sourceNames: Set<String>?): Set<NotificationSourceApp> {
    if (sourceNames == null) {
        return DEFAULT_SOURCES.toSet()
    }
    return sourceNames.mapNotNullTo(mutableSetOf()) { sourceName ->
        NotificationSourceApp.entries.firstOrNull { source -> source.name == sourceName }
    }
}

/**
 * 璁＄畻涓€娆℃潵婧愬紑鍏虫壒閲忓彉鏇村悗鐨勬寔涔呭寲鍚嶇О闆嗗悎銆? *
 * 鐢ㄩ€旇鏄庯細
 * - 鎶娾€滃綋鍓嶅凡淇濆瓨鏉ユ簮 + 鏈鎵归噺鍚仠鎿嶄綔鈥濇敹鍙ｆ垚绾嚱鏁帮紝渚夸簬浠撳簱澶嶇敤鍜屽崟鍏冩祴璇曘€? * - 杩欓噷涓嶄細瀵圭┖闆嗗悎鍋氶粯璁ゅ€煎洖濉紝纭繚鈥滃叏閮ㄥ叧闂€濆彲浠ヨ鐪熷疄鎸佷箙鍖栥€? *
 * 鍙傛暟璇存槑锛? * - currentSourceNames锛氬綋鍓嶆寔涔呭眰涓殑鏉ユ簮鍚嶇О闆嗗悎锛沗null` 琛ㄧず灏氭湭鍐欏叆杩囷紝闇€瑕佷粠榛樿鍏ㄥ紑璧风畻銆? * - sources锛氭湰娆￠渶瑕佺粺涓€鍚仠鐨勫簳灞傛潵婧愰泦鍚堛€? * - enabled锛氱洰鏍囩姸鎬侊紱`true` 琛ㄧず鍚敤锛宍false` 琛ㄧず鍏抽棴銆? */
internal fun applySourceEnabledChange(
    currentSourceNames: Set<String>?,
    sources: Set<NotificationSourceApp>,
    enabled: Boolean,
): Set<String> {
    val updatedSourceNames = currentSourceNames?.toMutableSet()
        ?: DEFAULT_SOURCES.mapTo(mutableSetOf()) { source -> source.name }
    val targetSourceNames = sources.mapTo(mutableSetOf()) { source -> source.name }
    if (enabled) {
        updatedSourceNames += targetSourceNames
    } else {
        updatedSourceNames -= targetSourceNames
    }
    return updatedSourceNames
}

private const val ONE_MINUTE_MILLIS = 60_000L

/** 涓?CSV 瀵煎嚭鍋氭渶灏忚浆涔夛紝閬垮厤閫楀彿鍜屽紩鍙风牬鍧忕粨鏋勩€?*/
private fun csvEscape(value: Any?): String {
    val text = value?.toString().orEmpty()
    return "\"${text.replace("\"", "\"\"")}\""
}

/** 鍐欏叆澶辫触淇℃伅鍓嶅仛涓€娆¤劚鏁忥紝閬垮厤闀挎暟瀛楃洿鎺ユ寔涔呭寲銆?*/
private fun sanitize(message: String): String {
    return message.replace(Regex("""\d{6,}"""), "******")
}

/** 瑙ｆ瀽棰勭畻瀛楃涓叉椂蹇界暐鑴忔暟鎹紝閬垮厤鏃х増鏈牸寮忓彉鍖栧鑷磋鍙栧穿婧冦€?*/
private fun parseMonthlyBudgets(rawValue: String): Map<String, Double> {
    if (rawValue.isBlank()) return emptyMap()
    return rawValue.split(";")
        .mapNotNull { entry ->
            val separatorIndex = entry.indexOf('=')
            if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) {
                return@mapNotNull null
            }
            val monthKey = entry.substring(0, separatorIndex)
            val amount = entry.substring(separatorIndex + 1).toDoubleOrNull() ?: return@mapNotNull null
            monthKey to amount
        }
        .toMap()
}

/** 搴忓垪鍖栨椂缁熶竴淇濈暀涓や綅浠ュ唴灏忔暟锛屾柟渚垮悗缁汉宸ユ帓鏌?DataStore 鍐呭銆?*/
private fun serializeMonthlyBudgets(budgets: Map<String, Double>): String {
    if (budgets.isEmpty()) return ""
    return budgets.toSortedMap().entries.joinToString(separator = ";") { (monthKey, amount) ->
        "$monthKey=${BUDGET_AMOUNT_FORMAT.format(amount)}"
    }
}

private val BUDGET_AMOUNT_FORMAT = DecimalFormat("0.##")

