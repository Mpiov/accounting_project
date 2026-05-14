package com.autobook.cn.core.data.impl

import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autobook.cn.core.data.AuthRepository
import com.autobook.cn.core.data.AppUpdateRepository
import com.autobook.cn.core.data.CloudConfidenceModel
import com.autobook.cn.core.data.CloudLearningRepository
import com.autobook.cn.core.data.CloudModelStore
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.LedgerSummaryStats
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.data.ModelTrainingSampleRepository
import com.autobook.cn.core.data.NotificationIngestor
import com.autobook.cn.core.data.PaymentRepository
import com.autobook.cn.core.data.NotificationTraceRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toDomain
import com.autobook.cn.core.data.mapper.toEntity
import com.autobook.cn.core.data.parser.enrichForProcessing
import com.autobook.cn.core.data.pipeline.TransactionProcessingPipeline
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.database.entity.LedgerStatsEntity
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.AppReleaseInfo
import com.autobook.cn.core.model.AuthSession
import com.autobook.cn.core.model.AuthState
import com.autobook.cn.core.model.CloudLearningState
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MembershipPaymentOrder
import com.autobook.cn.core.model.MembershipPaymentState
import com.autobook.cn.core.model.MembershipPlan
import com.autobook.cn.core.model.MembershipStatus
import com.autobook.cn.core.model.MembershipTier
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.AutobookBackendApi
import com.autobook.cn.core.network.BackendAppReleaseResponse
import com.autobook.cn.core.network.BackendCloudModelResponse
import com.autobook.cn.core.network.BackendCreateMembershipOrderRequest
import com.autobook.cn.core.network.BackendLoginRequest
import com.autobook.cn.core.network.BackendMembershipOrderResponse
import com.autobook.cn.core.network.BackendMembershipOrderStateResponse
import com.autobook.cn.core.network.BackendMembershipStatusResponse
import com.autobook.cn.core.network.BackendRefreshTokenRequest
import com.autobook.cn.core.network.BackendSmsCodeRequest
import com.autobook.cn.core.network.BackendTrainingSampleParser
import com.autobook.cn.core.network.BackendTrainingSamplesRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.settingsDataStore by preferencesDataStore(name = "autobook_settings")
private val Context.authDataStore by preferencesDataStore(name = "autobook_auth")

/**
 * 用途说明：
 * - 基于 Room 的账本仓储实现。
 *
 * 参数说明：
 * - ledgerEntryDao：账本表 DAO。
 */
@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
    private val modelTrainingSampleRepository: ModelTrainingSampleRepository,
) : LedgerRepository {
    override fun observeEntries(): Flow<List<LedgerEntry>> = ledgerEntryDao.observeAll().map { entries ->
        entries.map { it.toDomain() }
    }

    override fun observeSummaryStats(): Flow<LedgerSummaryStats> {
        return ledgerEntryDao.observeStats().map { stats ->
            // 设置页只消费两个聚合数字，直接读取缓存表，避免高频 `COUNT(*)` 或完整账本映射。
            stats.toSummaryStats()
        }
    }

    override suspend fun add(entry: LedgerEntry): Long {
        val entryToSave = entry.withSortId()
        val id = ledgerEntryDao.insert(entryToSave.toEntity())
        updateStatsAfterAdd(timestamp = entryToSave.timestamp)
        // 账本主键生成后回填训练样本，方便后续编辑或删除时找到同一条样本。
        runCatching {
            modelTrainingSampleRepository.linkLedgerEntry(
                notificationId = entryToSave.traceNotificationId,
                ledgerEntryId = id,
            )
        }
        return id
    }

    override suspend fun update(entry: LedgerEntry) {
        val existingEntry = getById(entry.id)
        val entryToSave = entry.withSortId(existingEntry?.sortId)
        ledgerEntryDao.update(entryToSave.toEntity())
        if (existingEntry != null) {
            updateStatsAfterTimestampChange(
                oldTimestamp = existingEntry.timestamp,
                newTimestamp = entryToSave.timestamp,
            )
        }
        if (entryToSave.editedManually) {
            runCatching {
                modelTrainingSampleRepository.markLedgerEdited(entryToSave)
            }
        }
    }

    override suspend fun delete(id: Long) {
        val existingEntry = getById(id)
        ledgerEntryDao.deleteById(id)
        if (existingEntry != null) {
            updateStatsAfterDelete(timestamp = existingEntry.timestamp)
            runCatching {
                modelTrainingSampleRepository.markLedgerDeleted(existingEntry)
            }
        }
    }

    override suspend fun getById(id: Long): LedgerEntry? = ledgerEntryDao.getById(id)?.toDomain()

    /**
     * 用途说明：
     * - 按通知主链路的判重规则查找疑似重复记录。
     *
     * 参数说明：
     * - candidate：当前待判重候选。
     */
    override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? {
        val duplicate = ledgerEntryDao.findPotentialDuplicate(
            amount = candidate.amount,
            direction = candidate.direction.name,
            windowStart = candidate.timestamp - DUPLICATE_WINDOW_MS,
            windowEnd = candidate.timestamp + DUPLICATE_WINDOW_MS,
        )
        return duplicate?.toDomain()
    }

    /**
     * 用途说明：
     * - 对图片导入候选执行分钟级历史判重。
     *
     * 参数说明：
     * - timestamp：候选时间戳。
     * - amount：候选金额。
     * - direction：候选方向。
     */
    override suspend fun findPotentialImageImportDuplicate(
        timestamp: Long?,
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        if (timestamp == null) {
            return null
        }
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
     * 用途说明：
     * - 在候选缺少时间时，用更宽松的规则探测历史重复风险。
     *
     * 参数说明：
     * - amount：候选金额。
     * - direction：候选方向。
     */
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
            appendLine("id,sortId,timestamp,direction,amount,channel,category,note,traceSource")
            rows.forEach { entry ->
                appendLine(
                    listOf(
                        entry.id,
                        entry.sortId,
                        entry.timestamp,
                        entry.direction.name,
                        entry.amount,
                        csvEscape(entry.channel),
                        csvEscape(entry.category),
                        csvEscape(entry.note),
                        csvEscape(entry.traceSource),
                    ).joinToString(","),
                )
            }
        }
    }

    /**
     * 为新账单补齐展示顺序 ID。
     *
     * 参数说明：
     * - existingSortId：编辑旧账单时已经存在的顺序 ID，优先保留。
     */
    private fun LedgerEntry.withSortId(existingSortId: Long? = null): LedgerEntry {
        val resolvedSortId = when {
            sortId > 0L -> sortId
            existingSortId != null && existingSortId > 0L -> existingSortId
            else -> AutobookSortIdGenerator.nextId()
        }
        return copy(sortId = resolvedSortId)
    }

    /**
     * 新增账单后增量维护统计缓存。
     *
     * 参数说明：
     * - timestamp：新账单的交易时间。
     */
    private suspend fun updateStatsAfterAdd(timestamp: Long) {
        val current = ledgerEntryDao.getStats()
        val next = LedgerStatsEntity(
            entryCount = (current?.entryCount ?: 0) + 1,
            firstTimestamp = minTimestamp(current?.firstTimestamp, timestamp),
            lastTimestamp = maxTimestamp(current?.lastTimestamp, timestamp),
        )
        ledgerEntryDao.upsertStats(next)
    }

    /**
     * 删除账单后增量维护统计缓存。
     *
     * 参数说明：
     * - timestamp：被删除账单原本的交易时间。
     */
    private suspend fun updateStatsAfterDelete(timestamp: Long) {
        val current = ledgerEntryDao.getStats() ?: return
        val nextCount = (current.entryCount - 1).coerceAtLeast(0)
        if (nextCount == 0) {
            ledgerEntryDao.upsertStats(emptyLedgerStats())
            return
        }

        val boundaryChanged = timestamp == current.firstTimestamp || timestamp == current.lastTimestamp
        val next = if (boundaryChanged) {
            current.copy(
                entryCount = nextCount,
                firstTimestamp = ledgerEntryDao.getFirstTimestamp(),
                lastTimestamp = ledgerEntryDao.getLastTimestamp(),
            )
        } else {
            current.copy(entryCount = nextCount)
        }
        ledgerEntryDao.upsertStats(next)
    }

    /**
     * 编辑账单时间后维护统计缓存。
     *
     * 参数说明：
     * - oldTimestamp：编辑前交易时间。
     * - newTimestamp：编辑后交易时间。
     */
    private suspend fun updateStatsAfterTimestampChange(
        oldTimestamp: Long,
        newTimestamp: Long,
    ) {
        if (oldTimestamp == newTimestamp) {
            return
        }
        val current = ledgerEntryDao.getStats() ?: return
        val boundaryChanged = oldTimestamp == current.firstTimestamp || oldTimestamp == current.lastTimestamp
        val next = if (boundaryChanged) {
            current.copy(
                firstTimestamp = ledgerEntryDao.getFirstTimestamp(),
                lastTimestamp = ledgerEntryDao.getLastTimestamp(),
            )
        } else {
            current.copy(
                firstTimestamp = minTimestamp(current.firstTimestamp, newTimestamp),
                lastTimestamp = maxTimestamp(current.lastTimestamp, newTimestamp),
            )
        }
        ledgerEntryDao.upsertStats(next)
    }
}

/**
 * 把统计缓存映射成设置页需要的轻量统计。
 *
 * 接收者说明：
 * - 当前账本统计缓存，数据库尚未初始化时为空。
 */
private fun LedgerStatsEntity?.toSummaryStats(): LedgerSummaryStats {
    val stats = this
    val first = stats?.firstTimestamp
    val last = stats?.lastTimestamp
    if (stats == null || stats.entryCount <= 0 || first == null || last == null) {
        return LedgerSummaryStats(dayCount = 0, entryCount = 0)
    }
    val firstDate = Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).toLocalDate()
    val lastDate = Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayCount = ChronoUnit.DAYS.between(firstDate, lastDate).toInt().coerceAtLeast(0) + 1
    return LedgerSummaryStats(
        dayCount = dayCount,
        entryCount = stats.entryCount,
    )
}

/**
 * 生成空账本统计缓存。
 *
 * 用途说明：
 * - 删除最后一笔账单后用固定空值覆盖缓存，避免设置页继续显示旧数据。
 */
private fun emptyLedgerStats(): LedgerStatsEntity = LedgerStatsEntity(
    entryCount = 0,
    firstTimestamp = null,
    lastTimestamp = null,
)

/**
 * 取较早时间戳。
 *
 * 参数说明：
 * - current：当前缓存时间，可能为空。
 * - candidate：候选时间。
 */
private fun minTimestamp(current: Long?, candidate: Long): Long {
    return current?.let { minOf(it, candidate) } ?: candidate
}

/**
 * 取较晚时间戳。
 *
 * 参数说明：
 * - current：当前缓存时间，可能为空。
 * - candidate：候选时间。
 */
private fun maxTimestamp(current: Long?, candidate: Long): Long {
    return current?.let { maxOf(it, candidate) } ?: candidate
}

/**
 * 用途说明：
 * - 基于 Room 的待确认仓储实现。
 *
 * 参数说明：
 * - pendingEntryDao：待确认表 DAO。
 */
@Singleton
class PendingRepositoryImpl @Inject constructor(
    private val pendingEntryDao: PendingEntryDao,
    private val modelTrainingSampleRepository: ModelTrainingSampleRepository,
) : PendingRepository {
    override fun observeEntries(): Flow<List<PendingEntry>> = pendingEntryDao.observeAll().map { entries ->
        entries.map { it.toDomain() }
    }

    override fun observeActiveCount(): Flow<Int> = pendingEntryDao.observeStats().map { stats ->
        // 底部徽标只需要有效待确认数量，直接读取缓存，避免常驻 `COUNT(*)`。
        stats?.activeCount ?: 0
    }

    override suspend fun add(entry: PendingEntry): Long {
        val entryToSave = entry.withSortId()
        val id = pendingEntryDao.insert(entryToSave.toEntity())
        if (!entryToSave.isFalsePositive) {
            updatePendingStats(delta = 1)
        }
        return id
    }

    override suspend fun update(entry: PendingEntry) {
        val existingEntry = getById(entry.id)
        val entryToSave = entry.withSortId(existingEntry?.sortId)
        pendingEntryDao.update(entryToSave.toEntity())
        if (existingEntry != null && existingEntry.isFalsePositive != entryToSave.isFalsePositive) {
            updatePendingStats(delta = if (entryToSave.isFalsePositive) -1 else 1)
        }
    }

    override suspend fun getById(id: Long): PendingEntry? = pendingEntryDao.getById(id)?.toDomain()

    override suspend fun confirm(id: Long): PendingEntry? {
        val item = getById(id) ?: return null
        pendingEntryDao.deleteById(id)
        if (!item.isFalsePositive) {
            updatePendingStats(delta = -1)
        }
        runCatching {
            modelTrainingSampleRepository.markPendingConfirmed(
                pendingEntryId = id,
                editedManually = false,
            )
        }
        return item
    }

    override suspend fun confirmEdited(
        id: Long,
        correctedEntry: LedgerEntry,
    ): PendingEntry? {
        val item = getById(id) ?: return null
        pendingEntryDao.deleteById(id)
        if (!item.isFalsePositive) {
            updatePendingStats(delta = -1)
        }
        runCatching {
            modelTrainingSampleRepository.markPendingConfirmed(
                pendingEntryId = id,
                editedManually = true,
                correctedEntry = correctedEntry,
            )
        }
        return item
    }

    override suspend fun ignore(id: Long) {
        val item = getById(id) ?: return
        pendingEntryDao.deleteById(id)
        if (!item.isFalsePositive) {
            updatePendingStats(delta = -1)
        }
        runCatching {
            modelTrainingSampleRepository.markPendingIgnored(pendingEntryId = item.id)
        }
    }

    override suspend fun markFalsePositive(id: Long) {
        val item = getById(id) ?: return
        pendingEntryDao.update(item.copy(isFalsePositive = true).toEntity())
        if (!item.isFalsePositive) {
            updatePendingStats(delta = -1)
        }
        runCatching {
            modelTrainingSampleRepository.markPendingFalsePositive(pendingEntryId = id)
        }
    }

    override suspend fun purgeUploadedFalsePositives(): Int {
        return pendingEntryDao.purgeUploadedFalsePositives(uploadedState = TRAINING_SAMPLE_UPLOAD_STATE_UPLOADED)
    }

    /**
     * 为待确认记录补齐展示顺序 ID。
     *
     * 参数说明：
     * - existingSortId：更新旧待确认时已经存在的顺序 ID，优先保留。
     */
    private fun PendingEntry.withSortId(existingSortId: Long? = null): PendingEntry {
        val resolvedSortId = when {
            sortId > 0L -> sortId
            existingSortId != null && existingSortId > 0L -> existingSortId
            else -> AutobookSortIdGenerator.nextId()
        }
        return copy(sortId = resolvedSortId)
    }

    /**
     * 增量维护待确认数量缓存。
     *
     * 参数说明：
     * - delta：有效待确认数量变化量，新增为 `+1`，确认/忽略/误识别为 `-1`。
     */
    private suspend fun updatePendingStats(delta: Int) {
        pendingEntryDao.applyActiveCountDelta(delta = delta)
    }
}

/**
 * 用途说明：
 * - 提供通知原文回看映射。
 *
 * 参数说明：
 * - notificationEventDao：通知事件表 DAO。
 */
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

/**
 * 用途说明：
 * - 邮箱账号仓储实现。
 * - 负责对接验证码创建、登录接口，并持久化服务器返回的令牌。
 *
 * 参数说明：
 * - context：应用级上下文，用于保存登录会话快照。
 * - backendApi：AutoBook 后端 API。
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendApi: AutobookBackendApi,
) : AuthRepository {
    /** 用途说明：串行化 refresh token 轮换，避免多个并发请求同时刷新导致刷新令牌互相撤销。 */
    private val refreshMutex = Mutex()

    /**
     * 用途说明：
     * - DataStore 键定义集中收口，后续换加密存储时只需要替换本仓储。
     */
    private object Keys {
        val userId = stringPreferencesKey("user_id")
        val phone = stringPreferencesKey("phone")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val accessTokenExpiresAt = longPreferencesKey("access_token_expires_at")
        val refreshTokenExpiresAt = longPreferencesKey("refresh_token_expires_at")
        val loggedInAt = longPreferencesKey("logged_in_at")
    }

    override val authState: Flow<AuthState> = context.authDataStore.data.map { preferences ->
        val session = buildSessionFromPreferences(preferences)
        AuthState(session = session)
    }

    /**
     * 用途说明：
     * - 请求邮箱验证码，对齐 `POST /v1/auth/sms-code`。
     *
     * 参数说明：
     * - phone：用户输入的邮箱；字段名沿用仓库契约，后续统一重命名为 email。
     */
    override suspend fun requestLoginCode(phone: String): OperationResult<Unit> {
        val normalizedEmail = normalizeEmailAddress(phone)
            ?: return OperationResult.TerminalFailure("请输入正确的邮箱地址")
        return when (
            val result = callBackend(AUTH_SMS_CODE_ENDPOINT) {
                backendApi.requestSmsCode(BackendSmsCodeRequest(phone = normalizedEmail))
            }
        ) {
            is OperationResult.Success -> OperationResult.Success(Unit)
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }

    /**
     * 用途说明：
     * - 邮箱验证码登录/注册，对齐 `POST /v1/auth/login`。
     *
     * 参数说明：
     * - phone：用户输入的邮箱；字段名沿用仓库契约，后续统一重命名为 email。
     * - code：邮箱验证码。
     */
    override suspend fun loginOrRegisterWithCode(
        phone: String,
        code: String,
    ): OperationResult<AuthSession> {
        val normalizedEmail = normalizeEmailAddress(phone)
            ?: return OperationResult.TerminalFailure("请输入正确的邮箱地址")
        if (!SMS_CODE_REGEX.matches(code.trim())) {
            return OperationResult.TerminalFailure("请输入 6 位邮箱验证码")
        }
        return when (
            val result = callBackend(AUTH_LOGIN_ENDPOINT) {
                backendApi.login(
                    BackendLoginRequest(
                        phone = normalizedEmail,
                        code = code.trim(),
                        deviceId = context.resolveDeviceId(),
                        appVersion = context.resolveAppVersion(),
                    ),
                )
            }
        ) {
            is OperationResult.Success -> {
                val data = result.data.data
                val session = AuthSession(
                    userId = data.userId,
                    phone = data.phone,
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    accessTokenExpiresAt = data.accessTokenExpiresAt,
                    refreshTokenExpiresAt = data.refreshTokenExpiresAt,
                    loggedInAt = System.currentTimeMillis(),
                    source = "server",
                )
                persistSession(session)
                OperationResult.Success(session)
            }
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }

    /**
     * 用途说明：
     * - 在业务接口被后端判定 access token 过期后，使用 refresh token 换取新会话。
     * - 刷新成功后会写回 DataStore，后续所有接口都会使用新 access token。
     *
     * 参数说明：
     * - expiredAccessToken：刚刚失败的 access token，用于判断是否已经被其它协程刷新过。
     */
    override suspend fun refreshSession(expiredAccessToken: String?): OperationResult<AuthSession> {
        return refreshMutex.withLock {
            val currentSession = buildSessionFromPreferences(context.authDataStore.data.first())
                ?: return@withLock OperationResult.TerminalFailure(
                    message = "请先使用邮箱登录/注册",
                    code = AUTH_TOKEN_EXPIRED_CODE,
                )
            if (expiredAccessToken != null && currentSession.accessToken != expiredAccessToken) {
                // 其它并发请求已经完成刷新，本次直接复用最新会话，避免多刷新一次。
                return@withLock OperationResult.Success(currentSession)
            }
            val now = System.currentTimeMillis()
            if (currentSession.refreshTokenExpiresAt > 0L && currentSession.refreshTokenExpiresAt <= now) {
                logout()
                return@withLock OperationResult.TerminalFailure(
                    message = "登录已过期，请重新登录",
                    code = AUTH_TOKEN_EXPIRED_CODE,
                )
            }
            when (
                val result = callBackend(AUTH_REFRESH_ENDPOINT) {
                    backendApi.refreshToken(BackendRefreshTokenRequest(refreshToken = currentSession.refreshToken))
                }
            ) {
                is OperationResult.Success -> {
                    val data = result.data.data
                    val refreshedSession = currentSession.copy(
                        accessToken = data.accessToken,
                        refreshToken = data.refreshToken,
                        accessTokenExpiresAt = data.accessTokenExpiresAt,
                        refreshTokenExpiresAt = data.refreshTokenExpiresAt,
                    )
                    persistSession(refreshedSession)
                    OperationResult.Success(refreshedSession)
                }
                is OperationResult.RetryableFailure -> result
                is OperationResult.TerminalFailure -> {
                    if (result.code == AUTH_TOKEN_EXPIRED_CODE) {
                        // refresh token 也失效时清除本地会话，避免界面继续展示“已登录”。
                        logout()
                    }
                    result
                }
            }
        }
    }

    /**
     * 用途说明：
     * - 清除本地会话快照，退出后所有会员能力立即回到免费门禁。
     */
    override suspend fun logout() {
        context.authDataStore.edit { preferences -> preferences.clear() }
    }

    /**
     * 用途说明：
     * - 从 DataStore 中恢复服务器会话快照。
     *
     * 参数说明：
     * - preferences：DataStore 当前快照。
     */
    private fun buildSessionFromPreferences(
        preferences: androidx.datastore.preferences.core.Preferences,
    ): AuthSession? {
        val userId = preferences[Keys.userId]?.takeIf(String::isNotBlank) ?: return null
        val phone = preferences[Keys.phone]?.takeIf(String::isNotBlank) ?: return null
        val accessToken = preferences[Keys.accessToken]?.takeIf(String::isNotBlank) ?: return null
        val refreshToken = preferences[Keys.refreshToken]?.takeIf(String::isNotBlank) ?: return null
        return AuthSession(
            userId = userId,
            phone = phone,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = preferences[Keys.accessTokenExpiresAt] ?: 0L,
            refreshTokenExpiresAt = preferences[Keys.refreshTokenExpiresAt] ?: 0L,
            loggedInAt = preferences[Keys.loggedInAt] ?: 0L,
        )
    }

    /**
     * 用途说明：
     * - 持久化服务端返回的完整会话快照。
     * - 登录态只保存服务器返回值，本地不伪造会员或模型权限。
     *
     * 参数说明：
     * - session：待保存的最新会话。
     */
    private suspend fun persistSession(session: AuthSession) {
        context.authDataStore.edit { preferences ->
            preferences[Keys.userId] = session.userId
            preferences[Keys.phone] = session.phone
            preferences[Keys.accessToken] = session.accessToken
            preferences[Keys.refreshToken] = session.refreshToken
            preferences[Keys.accessTokenExpiresAt] = session.accessTokenExpiresAt
            preferences[Keys.refreshTokenExpiresAt] = session.refreshTokenExpiresAt
            preferences[Keys.loggedInAt] = session.loggedInAt
        }
    }
}

/**
 * 用途说明：
 * - 会员状态仓储实现。
 * - 会员状态必须通过 `GET /v1/membership/status` 由服务器确认；本地未登录或后端未配置时都不会授予会员能力。
 *
 * 参数说明：
 * - authRepository：读取当前邮箱登录会话；内部会话字段名暂沿用 `phone`。
 * - backendApi：AutoBook 后端 API。
 * - settingsRepository：用于在服务器确认会员生效后恢复因免费体验耗尽关闭的自动记账。
 */
@Singleton
class MembershipRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendApi: AutobookBackendApi,
    private val settingsRepository: SettingsRepository,
) : MembershipRepository {
    private val refreshedStatus = MutableStateFlow<MembershipStatus?>(null)

    override val membershipStatus: Flow<MembershipStatus> = combine(
        authRepository.authState,
        refreshedStatus,
    ) { authState, status ->
        val session = authState.session
        when {
            session == null -> loggedOutMembershipStatus()
            status?.userId == session.userId -> status
            else -> MembershipStatus(
                userId = session.userId,
                phone = session.phone,
                source = "server-required",
                message = "已登录，等待从服务器刷新会员状态。",
            )
        }
    }

    /**
     * 用途说明：
     * - 刷新服务器权威会员状态，对齐 `GET /v1/membership/status`。
     */
    override suspend fun refresh(): MembershipStatus {
        val currentStatus = when {
            authRepository.authState.first().session == null -> loggedOutMembershipStatus()
            else -> when (
                val result = callAuthenticatedBackend(
                    endpointLabel = MEMBERSHIP_STATUS_ENDPOINT,
                    authRepository = authRepository,
                    loginRequiredMessage = "请先使用邮箱登录/注册，再刷新会员状态",
                ) { authorization ->
                    backendApi.membershipStatus(authorization)
                }
            ) {
                is OperationResult.Success -> {
                    val refreshedSession = authRepository.authState.first().session
                        ?: return loggedOutMembershipStatus()
                    result.data.data.toMembershipStatus(
                        fallbackSession = refreshedSession,
                        serverTime = result.data.serverTime,
                    )
                }
                is OperationResult.RetryableFailure -> MembershipStatus(
                    userId = authRepository.authState.first().session?.userId,
                    phone = authRepository.authState.first().session?.phone,
                    serverVerified = false,
                    refreshedAt = System.currentTimeMillis(),
                    source = "server-error",
                    message = result.message,
                )
                is OperationResult.TerminalFailure -> MembershipStatus(
                    userId = authRepository.authState.first().session?.userId,
                    phone = authRepository.authState.first().session?.phone,
                    serverVerified = false,
                    refreshedAt = System.currentTimeMillis(),
                    source = "server-rejected",
                    message = result.message,
                )
            }
        }
        restoreAutoAccountingIfMember(currentStatus)
        refreshedStatus.value = currentStatus
        return currentStatus
    }

    /**
     * 用途说明：
     * - 免费用户自动记账真正落账前，由服务器原子扣减一次体验额度。
     * - 服务端返回额度不足时，本地不得继续写账本，避免删除 App 或换设备绕过次数限制。
     */
    override suspend fun recordAutoLedgerTrialSuccess(): OperationResult<MembershipStatus> {
        return when (
            val result = callAuthenticatedBackend(
                endpointLabel = AUTO_LEDGER_TRIAL_SUCCESS_ENDPOINT,
                authRepository = authRepository,
                loginRequiredMessage = "自动入账需要先登录邮箱账号，已转入待确认",
            ) { authorization ->
                backendApi.recordAutoLedgerTrialSuccess(authorization)
            }
        ) {
            is OperationResult.Success -> {
                val refreshedSession = authRepository.authState.first().session
                    ?: return OperationResult.TerminalFailure("登录已过期，请重新登录")
                val status = result.data.data.toMembershipStatus(
                    fallbackSession = refreshedSession,
                    serverTime = result.data.serverTime,
                )
                restoreAutoAccountingIfMember(status)
                // 立即更新会员状态流，设置页和自动记账开关会使用服务端最新剩余额度。
                refreshedStatus.value = status
                OperationResult.Success(status)
            }
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> {
                if (result.code == AUTO_LEDGER_TRIAL_EXHAUSTED_CODE) {
                    // 额度耗尽是服务端权威状态，失败后再拉一次会员状态，让设置页剩余次数马上归零。
                    refresh()
                }
                result
            }
        }
    }

    /**
     * 用途说明：
     * - 构造未登录时的免费状态，避免任何本地缓存误开会员能力。
     */
    private fun loggedOutMembershipStatus(): MembershipStatus {
        return MembershipStatus(message = "未登录，会员状态需要邮箱登录后从服务器确认。")
    }

    /**
     * 用途说明：
     * - 服务器确认会员生效后，恢复此前因免费体验耗尽被系统关闭的自动记账。
     * - 如果用户是手动关闭自动记账，设置仓储会保留关闭状态，不会在这里强行打开。
     *
     * 参数说明：
     * - status：服务器刷新或扣次接口返回的会员状态。
     */
    private suspend fun restoreAutoAccountingIfMember(status: MembershipStatus) {
        if (status.isMember) {
            settingsRepository.restoreAutoAccountingAfterMembershipActivation(
                allowLegacyTrialExhaustedRecovery = status.autoLedgerTrialRemaining <= 0 ||
                    status.autoLedgerTrialUsed >= status.autoLedgerTrialLimit,
            )
        }
    }
}

/**
 * 用途说明：
 * - 会员支付仓储实现。
 * - 当前会请求 `/v1/payments/membership-orders` 创建服务器订单，真实微信支付参数后续由服务端补齐。
 */
@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendApi: AutobookBackendApi,
) : PaymentRepository {
    /**
     * 用途说明：
     * - 创建会员支付订单，对齐 `POST /v1/payments/membership-orders`。
     *
     * 参数说明：
     * - plan：用户选择的会员套餐。
     */
    override suspend fun createMembershipOrder(plan: MembershipPlan): OperationResult<MembershipPaymentOrder> {
        return when (
            val result = callAuthenticatedBackend(
                endpointLabel = MEMBERSHIP_ORDER_CREATE_ENDPOINT,
                authRepository = authRepository,
                loginRequiredMessage = "请先使用邮箱登录/注册，再开通会员",
            ) { authorization ->
                backendApi.createMembershipOrder(
                    authorization = authorization,
                    body = BackendCreateMembershipOrderRequest(plan = plan.name),
                )
            }
        ) {
            is OperationResult.Success -> OperationResult.Success(result.data.data.toDomainOrder())
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }

    /**
     * 用途说明：
     * - 查询会员支付状态，对齐 `GET /v1/payments/membership-orders/{orderId}`。
     *
     * 参数说明：
     * - orderId：服务器业务订单号。
     */
    override suspend fun queryPaymentState(orderId: String): OperationResult<MembershipPaymentState> {
        return when (
            val result = callAuthenticatedBackend(
                endpointLabel = MEMBERSHIP_ORDER_QUERY_ENDPOINT,
                authRepository = authRepository,
                loginRequiredMessage = "请先使用邮箱登录/注册，再查询会员订单",
            ) { authorization ->
                backendApi.queryMembershipOrder(
                    authorization = authorization,
                    orderId = orderId,
                )
            }
        ) {
            is OperationResult.Success -> OperationResult.Success(result.data.data.toPaymentState())
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }
}

/**
 * 用途说明：
 * - App 新版本检查仓储实现。
 * - 这里只读取服务端小体积元数据，不下载 APK，避免 4Mbps 服务器被后台流量占满。
 *
 * 参数说明：
 * - backendApi：AutoBook 后端 API，用于访问公开版本元数据接口。
 */
@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val backendApi: AutobookBackendApi,
) : AppUpdateRepository {
    /**
     * 用途说明：
     * - 查询 Android stable 最新版本，并把后端响应转换成客户端领域模型。
     *
     * 参数说明：
     * - currentVersionCode：当前安装包 versionCode。
     * - currentVersionName：当前安装包 versionName。
     */
    override suspend fun checkLatestRelease(
        currentVersionCode: Int,
        currentVersionName: String,
    ): OperationResult<AppReleaseInfo> {
        return when (
            val result = callBackend(APP_RELEASE_LATEST_ENDPOINT) {
                backendApi.latestAppRelease(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                )
            }
        ) {
            is OperationResult.Success -> OperationResult.Success(result.data.data.toAppReleaseInfo())
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }
}

/**
 * 用途说明：
 * - 云端学习仓储实现。
 * - 负责会员门禁、设置开关、本地样本导出、全局样本池上传和全局模型拉取。
 *
 * 参数说明：
 * - context：应用级上下文，用于生成设备与版本信息。
 * - settingsRepository：读取和写入用户改善计划开关。
 * - authRepository：读取当前邮箱登录会话；内部会话字段名暂沿用 `phone`。
 * - membershipRepository：判断当前用户是否具备会员模型能力。
 * - modelTrainingSampleRepository：导出待上传训练样本。
 * - cloudModelStore：持久化服务器发布的全局模型，并为解析器提供本地可用版本。
 * - backendApi：AutoBook 后端 API。
 */
@Singleton
class CloudLearningRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val membershipRepository: MembershipRepository,
    private val modelTrainingSampleRepository: ModelTrainingSampleRepository,
    private val pendingRepository: PendingRepository,
    private val cloudModelStore: CloudModelStore,
    private val backendApi: AutobookBackendApi,
) : CloudLearningRepository {
    private val syncSnapshot = MutableStateFlow(CloudLearningSyncSnapshot())

    override val cloudLearningState: Flow<CloudLearningState> = combine(
        settingsRepository.settings,
        membershipRepository.membershipStatus,
        syncSnapshot,
        cloudModelStore.activeModel,
    ) { settings, membership, sync, activeModel ->
        val effectiveEnabled = membership.canUseMachineLearning
        val usableModel = activeModel?.takeIf { model -> model.isUsableAt() }
        CloudLearningState(
            enabledByUser = settings.cloudLearningEnabled,
            isMember = membership.isMember,
            effectiveEnabled = effectiveEnabled,
            lastUploadAt = sync.lastUploadAt,
            lastModelPullAt = sync.lastModelPullAt,
            activeModelVersion = when {
                !membership.canUseMachineLearning -> "local-rules-v1"
                usableModel != null -> usableModel.modelVersion
                else -> sync.modelVersion ?: "local-linear-confidence-v1"
            },
            message = when {
                effectiveEnabled -> "会员机器学习已开启，可拉取服务器最新全局模型；样本上传由用户改善计划单独控制。"
                membership.canUseMachineLearning -> "会员机器学习已开启，可继续使用服务器全局模型。"
                !membership.serverVerified -> membership.message
                else -> "免费版仅使用普通规则算法，开通会员后可启用机器学习。"
            },
        )
    }

    /**
     * 用途说明：
     * - 写入历史云端学习开关，保留给旧版本或调试入口使用。
     *
     * 参数说明：
     * - enabled：用户选择的开关状态。
     */
    override suspend fun setCloudLearningEnabled(enabled: Boolean) {
        settingsRepository.setCloudLearningEnabled(enabled)
    }

    /**
     * 用途说明：
     * - 上传训练样本，对齐 `POST /v1/cloud-learning/training-samples`。
     * - 请求体会包含通知原文，因此只在用户登录且“用户改善计划”开启时发送具备有效反馈标签的样本。
     */
    override suspend fun uploadTrainingSamples(): OperationResult<Unit> {
        val settings = settingsRepository.settings.first()
        if (!settings.userImprovementPlanEnabled) {
            return OperationResult.TerminalFailure("用户改善计划已关闭，不会上传通知原文")
        }

        val uploadBatch = modelTrainingSampleRepository.loadUploadBatch(TRAINING_SAMPLE_UPLOAD_BATCH_LIMIT)
        if (uploadBatch.jsonl.isBlank() || uploadBatch.sampleIds.isEmpty()) {
            return OperationResult.Success(Unit)
        }
        val samples = BackendTrainingSampleParser.parseJsonl(uploadBatch.jsonl)
        if (samples.isEmpty()) {
            return OperationResult.Success(Unit)
        }
        val attemptedAt = System.currentTimeMillis()
        modelTrainingSampleRepository.markUploadBatchAttempted(
            sampleIds = uploadBatch.sampleIds,
            attemptedAt = attemptedAt,
        )
        return when (
            val result = callAuthenticatedBackend(
                endpointLabel = TRAINING_SAMPLE_UPLOAD_ENDPOINT,
                authRepository = authRepository,
                loginRequiredMessage = "请先使用邮箱登录/注册，再上传训练样本",
            ) { authorization ->
                backendApi.uploadTrainingSamples(
                    authorization = authorization,
                    body = BackendTrainingSamplesRequest(
                        clientBatchId = "android-${UUID.randomUUID()}",
                        deviceId = context.resolveDeviceId(),
                        appVersion = context.resolveAppVersion(),
                        samples = samples,
                    ),
                )
            }
        ) {
            is OperationResult.Success -> {
                val uploadedAt = System.currentTimeMillis()
                val response = result.data.data
                if (response.rejected == 0) {
                    // 服务端按本地 sampleLocalId 做 upsert，成功响应后可精确标记本批已同步。
                    modelTrainingSampleRepository.markUploadBatchUploaded(
                        sampleIds = uploadBatch.sampleIds,
                        uploadedAt = uploadedAt,
                    )
                    // 误识别待确认项只为上传负样本而本地保留；服务端确认接收后即可清理隐藏残留。
                    pendingRepository.purgeUploadedFalsePositives()
                }
                syncSnapshot.value = syncSnapshot.value.copy(lastUploadAt = uploadedAt)
                OperationResult.Success(Unit)
            }
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }

    /**
     * 用途说明：
     * - 拉取全局模型，对齐 `GET /v1/cloud-learning/global-model`。
     * - 成功拿到新模型后写入本地模型仓库，会员解析器会在下一次通知解析时优先读取服务器权重。
     */
    override suspend fun pullCloudModel(): OperationResult<Unit> {
        val membership = membershipRepository.refresh()
        if (!membership.canUseMachineLearning) {
            return OperationResult.TerminalFailure("当前账号不是会员或未开启机器学习，继续使用普通规则算法")
        }
        val currentModelVersion = cloudModelStore.getActiveModel()?.modelVersion ?: syncSnapshot.value.modelVersion
        return when (
            val result = callAuthenticatedBackend(
                endpointLabel = GLOBAL_MODEL_ENDPOINT,
                authRepository = authRepository,
                loginRequiredMessage = "请先使用邮箱登录/注册，再拉取全局模型",
            ) { authorization ->
                backendApi.pullGlobalModel(
                    authorization = authorization,
                    currentVersion = currentModelVersion,
                )
            }
        ) {
            is OperationResult.Success -> {
                val model = result.data.data
                val pulledAt = System.currentTimeMillis()
                val cloudModel = model.toCloudConfidenceModel()
                if (!model.notModified && cloudModel.isUsableAt(now = pulledAt)) {
                    // 只有带有效权重的服务器模型才覆盖本地缓存，避免占位响应清掉可用旧模型。
                    cloudModelStore.saveGlobalModel(cloudModel)
                }
                val activeModelVersion = cloudModelStore.getActiveModel()?.modelVersion
                syncSnapshot.value = syncSnapshot.value.copy(
                    lastModelPullAt = pulledAt,
                    modelVersion = model.modelVersion.takeIf(String::isNotBlank)
                        ?: activeModelVersion
                        ?: syncSnapshot.value.modelVersion,
                )
                OperationResult.Success(Unit)
            }
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
        }
    }
}

/**
 * 用途说明：
 * - 云端学习最近同步状态。
 *
 * 参数说明：
 * - lastUploadAt：最近一次成功上传样本时间。
 * - lastModelPullAt：最近一次成功拉取模型时间。
 * - modelVersion：最近一次服务器返回的模型版本。
 */
private data class CloudLearningSyncSnapshot(
    val lastUploadAt: Long? = null,
    val lastModelPullAt: Long? = null,
    val modelVersion: String? = null,
)

/**
 * 用途说明：
 * - 把后端 App 版本元数据映射为客户端领域模型。
 *
 * 参数说明：
 * - 接收者：后端 `GET /v1/app/releases/latest` 返回的数据体。
 */
private fun BackendAppReleaseResponse.toAppReleaseInfo(): AppReleaseInfo {
    return AppReleaseInfo(
        latestVersionCode = latestVersionCode.coerceAtLeast(0),
        latestVersionName = latestVersionName.trim(),
        minSupportedVersionCode = minSupportedVersionCode.coerceAtLeast(0),
        forceUpdate = forceUpdate,
        apkUrl = apkUrl.trim(),
        apkSizeBytes = apkSizeBytes.coerceAtLeast(0L),
        sha256 = sha256?.trim()?.takeIf(String::isNotBlank),
        releaseNotes = releaseNotes.map(String::trim).filter(String::isNotBlank),
        publishedAt = publishedAt?.takeIf { value -> value > 0L },
        notModified = notModified,
    )
}

/**
 * 用途说明：
 * - 把后端模型响应映射为客户端可落盘的全局置信度模型。
 *
 * 参数说明：
 * - 接收者：后端 `GET /v1/cloud-learning/global-model` 返回的数据体。
 */
private fun BackendCloudModelResponse.toCloudConfidenceModel(): CloudConfidenceModel {
    return CloudConfidenceModel(
        modelVersion = modelVersion,
        modelScope = modelScope,
        modelType = modelType,
        weights = weights.filterValues { value -> value.isFinite() },
        thresholds = thresholds.filterValues { value -> value.isFinite() },
        publishedAt = publishedAt,
        validUntil = validUntil,
        trainedSampleCount = trainedSampleCount,
        signature = signature,
    )
}

/**
 * 用途说明：
 * - 把服务端会员状态映射为领域模型。
 *
 * 参数说明：
 * - fallbackSession：当前登录会话，用于服务端字段为空时保持界面可读。
 * - serverTime：服务端统一响应里的当前时间。
 */
private fun BackendMembershipStatusResponse.toMembershipStatus(
    fallbackSession: AuthSession,
    serverTime: Long,
): MembershipStatus {
    val tierValue = runCatching { MembershipTier.valueOf(tier) }.getOrDefault(MembershipTier.FREE)
    return MembershipStatus(
        userId = userId ?: fallbackSession.userId,
        phone = phone ?: fallbackSession.phone,
        tier = tierValue,
        expiresAt = expiresAt,
        serverVerified = serverVerified,
        serverTime = serverTime,
        refreshedAt = System.currentTimeMillis(),
        modelConfidenceEnabled = modelConfidenceEnabled,
        cloudLearningEnabled = cloudLearningEnabled,
        autoLedgerTrialLimit = autoLedgerTrialLimit.coerceAtLeast(0),
        autoLedgerTrialUsed = autoLedgerTrialUsed.coerceAtLeast(0),
        autoLedgerTrialRemaining = autoLedgerTrialRemaining.coerceAtLeast(0),
        source = "server",
        message = if (tierValue == MembershipTier.MEMBER && serverVerified) {
            "会员状态已由服务器确认，机器学习能力按服务端开关启用。"
        } else {
            "服务器确认当前为免费版，仅使用普通规则算法。"
        },
    )
}

/**
 * 用途说明：
 * - 把服务端订单响应映射为客户端支付订单模型。
 */
private fun BackendMembershipOrderResponse.toDomainOrder(): MembershipPaymentOrder {
    val params = wechatAppPayParams
    return MembershipPaymentOrder(
        orderId = orderId,
        // 当前正式开放的是限时年卡，服务端异常返回未知套餐时也按年卡兜底展示。
        plan = runCatching { MembershipPlan.valueOf(plan) }.getOrDefault(MembershipPlan.YEARLY),
        amountCents = amountCents,
        appId = params?.appId.orEmpty(),
        partnerId = params?.partnerId.orEmpty(),
        prepayId = params?.prepayId.orEmpty(),
        packageValue = params?.packageValue.orEmpty(),
        nonceStr = params?.nonceStr.orEmpty(),
        timestamp = params?.timestamp.orEmpty(),
        sign = params?.sign.orEmpty(),
        expiresAt = expiresAt,
    )
}

/**
 * 用途说明：
 * - 把服务端订单状态响应映射为客户端枚举。
 */
private fun BackendMembershipOrderStateResponse.toPaymentState(): MembershipPaymentState {
    return runCatching { MembershipPaymentState.valueOf(paymentState) }
        .getOrDefault(MembershipPaymentState.FAILED)
}

/**
 * 用途说明：
 * - 生成后端识别设备所需的稳定 ID。
 */
private fun Context.resolveDeviceId(): String {
    return AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ANDROID_ID)
        ?: "android-unknown-device"
}

/**
 * 用途说明：
 * - 读取当前 App 版本号，用于服务端记录登录和训练样本来源。
 */
private fun Context.resolveAppVersion(): String {
    return runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
    }.getOrDefault("0.0.0")
}

/**
 * 用途说明：
 * - 归一化登录邮箱。
 *
 * 参数说明：
 * - rawEmail：用户输入的邮箱文本。
 */
private fun normalizeEmailAddress(rawEmail: String): String? {
    val normalized = rawEmail.trim().lowercase()
    return normalized.takeIf { EMAIL_REGEX.matches(it) }
}

private val EMAIL_REGEX = Regex("""^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$""", RegexOption.IGNORE_CASE)
private val SMS_CODE_REGEX = Regex("""\d{6}""")

/** 用途说明：发送邮箱验证码的后端接口路径，路径名后续再统一调整。 */
private const val AUTH_SMS_CODE_ENDPOINT = "POST /v1/auth/sms-code"

/** 用途说明：邮箱验证码登录或注册的后端接口路径。 */
private const val AUTH_LOGIN_ENDPOINT = "POST /v1/auth/login"

/** 用途说明：使用 refresh token 刷新访问令牌的后端接口路径。 */
private const val AUTH_REFRESH_ENDPOINT = "POST /v1/auth/refresh"

/** 用途说明：查询服务器权威会员状态的后端接口路径。 */
private const val MEMBERSHIP_STATUS_ENDPOINT = "GET /v1/membership/status"

/** 用途说明：扣减免费自动记账体验次数的后端接口路径。 */
private const val AUTO_LEDGER_TRIAL_SUCCESS_ENDPOINT = "POST /v1/auto-ledger/trial-success"

/** 用途说明：服务端表示免费自动记账体验已经耗尽的稳定错误码。 */
private const val AUTO_LEDGER_TRIAL_EXHAUSTED_CODE = "AUTO_LEDGER_TRIAL_EXHAUSTED"

/** 用途说明：创建会员支付订单的后端接口路径。 */
private const val MEMBERSHIP_ORDER_CREATE_ENDPOINT = "POST /v1/payments/membership-orders"

/** 用途说明：查询会员支付订单的后端接口路径。 */
private const val MEMBERSHIP_ORDER_QUERY_ENDPOINT = "GET /v1/payments/membership-orders/{orderId}"

/** 用途说明：检查 App 最新稳定版元数据的后端接口标签。 */
private const val APP_RELEASE_LATEST_ENDPOINT = "GET /v1/app/releases/latest"

/** 用途说明：上传用户改善计划训练样本到全局样本池的后端接口路径。 */
private const val TRAINING_SAMPLE_UPLOAD_ENDPOINT = "POST /v1/cloud-learning/training-samples"

/** 用途说明：单次上传训练样本的最大条数，和后端请求体上限保持一致。 */
private const val TRAINING_SAMPLE_UPLOAD_BATCH_LIMIT = 200

/** 用途说明：训练样本已被服务器确认接收后的本地状态值。 */
private const val TRAINING_SAMPLE_UPLOAD_STATE_UPLOADED = "UPLOADED"

/** 用途说明：拉取会员机器学习全局模型数据的后端接口路径。 */
private const val GLOBAL_MODEL_ENDPOINT = "GET /v1/cloud-learning/global-model"

/**
 * 用途说明：
 * - 基于 DataStore 的设置仓储实现。
 *
 * 参数说明：
 * - context：应用级上下文。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    /**
     * 用途说明：
     * - DataStore 键定义集中收口。
     */
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val autoAccountingEnabled = booleanPreferencesKey("auto_accounting_enabled")
        val autoAccountingDisabledByTrialExhaustion =
            booleanPreferencesKey("auto_accounting_disabled_by_trial_exhaustion")
        val autoLedgerSuccessNotificationEnabled =
            booleanPreferencesKey("auto_ledger_success_notification_enabled")
        val cloudAnalysisEnabled = booleanPreferencesKey("cloud_analysis_enabled")
        val cloudLearningEnabled = booleanPreferencesKey("cloud_learning_enabled")
        val userImprovementPlanEnabled = booleanPreferencesKey("user_improvement_plan_enabled")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val notificationRetentionDays = intPreferencesKey("notification_retention_days")
        val screenshotRetentionDays = intPreferencesKey("screenshot_retention_days")
        val monthlyBudgets = stringPreferencesKey("monthly_budgets")
        val enabledSources = stringSetPreferencesKey("enabled_sources")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
            autoAccountingEnabled = preferences[Keys.autoAccountingEnabled] ?: true,
            autoAccountingDisabledByTrialExhaustion =
                preferences[Keys.autoAccountingDisabledByTrialExhaustion] ?: false,
            autoLedgerSuccessNotificationEnabled =
                preferences[Keys.autoLedgerSuccessNotificationEnabled] ?: true,
            cloudAnalysisEnabled = preferences[Keys.cloudAnalysisEnabled] ?: true,
            cloudLearningEnabled = preferences[Keys.cloudLearningEnabled] ?: true,
            userImprovementPlanEnabled = preferences[Keys.userImprovementPlanEnabled] ?: true,
            captureEnabled = preferences[Keys.captureEnabled] ?: false,
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
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.autoAccountingEnabled] = enabled
            // 用户手动切换开关时清理“体验耗尽自动关闭”标记，避免后续会员刷新覆盖用户选择。
            preferences[Keys.autoAccountingDisabledByTrialExhaustion] = false
        }
    }

    /**
     * 用途说明：
     * - 持久化自动入账成功通知开关。
     *
     * 参数说明：
     * - enabled：是否在自动入账成功后弹出可点击通知。
     */
    override suspend fun setAutoLedgerSuccessNotificationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.autoLedgerSuccessNotificationEnabled] = enabled
        }
    }

    /**
     * 用途说明：
     * - 免费自动入账体验耗尽时关闭总开关，并保留系统关闭原因。
     * - 后续服务器确认用户成为会员时，可以根据这个标记自动恢复自动记账。
     */
    override suspend fun disableAutoAccountingForTrialExhaustion() {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.autoAccountingEnabled] = false
            preferences[Keys.autoAccountingDisabledByTrialExhaustion] = true
        }
    }

    /**
     * 用途说明：
     * - 会员状态生效后恢复因体验耗尽被系统关闭的自动记账。
     * - 兼容旧版本：如果旧客户端已经把开关关掉但尚未写入原因标记，也恢复一次，修复已付费用户卡在免费门禁的问题。
     */
    override suspend fun restoreAutoAccountingAfterMembershipActivation(
        allowLegacyTrialExhaustedRecovery: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            val autoAccountingEnabled = preferences[Keys.autoAccountingEnabled] ?: true
            val disabledByTrial = preferences[Keys.autoAccountingDisabledByTrialExhaustion] ?: false
            val legacyDisabledBeforeReasonKey =
                allowLegacyTrialExhaustedRecovery && !preferences.contains(Keys.autoAccountingDisabledByTrialExhaustion)
            if (!autoAccountingEnabled && (disabledByTrial || legacyDisabledBeforeReasonKey)) {
                preferences[Keys.autoAccountingEnabled] = true
            }
            preferences[Keys.autoAccountingDisabledByTrialExhaustion] = false
        }
    }

    override suspend fun setCloudAnalysisEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.cloudAnalysisEnabled] = enabled }
    }

    override suspend fun setCloudLearningEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.cloudLearningEnabled] = enabled }
    }

    /**
     * 用途说明：
     * - 持久化用户改善计划开关。
     *
     * 参数说明：
     * - enabled：是否允许后台定时上传通知原文训练样本。
     */
    override suspend fun setUserImprovementPlanEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.userImprovementPlanEnabled] = enabled }
    }

    override suspend fun setCaptureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.captureEnabled] = enabled }
    }

    override suspend fun setNotificationRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.notificationRetentionDays] = days.coerceIn(1, 90) }
    }

    override suspend fun setScreenshotRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.screenshotRetentionDays] = days.coerceIn(1, 30) }
    }

    override suspend fun setMonthlyBudget(monthKey: String, amount: Double) {
        context.settingsDataStore.edit { preferences ->
            val existingBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()).toMutableMap()
            existingBudgets[monthKey] = amount.coerceAtLeast(0.0)
            preferences[Keys.monthlyBudgets] = serializeMonthlyBudgets(existingBudgets)
        }
    }

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

/**
 * 用途说明：
 * - 通知入账主链路的仓储协调实现。
 *
 * 参数说明：
 * - notificationEventDao：通知事件表 DAO。
 * - pipeline：交易处理流水线。
 * - settingsRepository：运行时设置来源。
 */
@Singleton
class NotificationIngestorImpl @Inject constructor(
    private val notificationEventDao: NotificationEventDao,
    private val pipeline: TransactionProcessingPipeline,
    private val settingsRepository: SettingsRepository,
    private val modelTrainingSampleRepository: ModelTrainingSampleRepository,
) : NotificationIngestor {
    override suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome> {
        val enriched = event.enrichForProcessing()
        val id = notificationEventDao.insert(enriched.toEntity())
        val persisted = enriched.copy(id = id)
        val settings = settingsRepository.settings.first()
        if (!settings.autoAccountingEnabled) {
            recordRawNotificationSafely(
                notificationId = id,
                event = persisted,
                status = NotificationProcessStatus.FILTERED_OUT,
                reason = "Auto accounting disabled by user settings",
            )
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.FILTERED,
                status = NotificationProcessStatus.FILTERED_OUT,
                message = "Auto accounting disabled by user settings",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

        if (persisted.sourceApp !in settings.enabledSources) {
            recordRawNotificationSafely(
                notificationId = id,
                event = persisted,
                status = NotificationProcessStatus.FILTERED_OUT,
                reason = "Source disabled by user settings",
            )
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.FILTERED,
                status = NotificationProcessStatus.FILTERED_OUT,
                message = "Source disabled by user settings",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

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
                // 临时失败也保存原文样本，但上传批次会过滤掉无训练标签的原文样本。
                recordRawNotificationSafely(
                    notificationId = id,
                    event = persisted,
                    status = NotificationProcessStatus.FAILED_RETRYABLE,
                    reason = sanitize(result.message),
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
                recordRawNotificationSafely(
                    notificationId = id,
                    event = persisted,
                    status = NotificationProcessStatus.FAILED_TERMINAL,
                    reason = sanitize(result.message),
                )
                result
            }
        }
    }

    /**
     * 用途说明：
     * - 记录没有进入交易候选样本的通知原文。
     * - 这里吞掉样本记录失败，避免训练样本辅助链路影响用户正常记账流程。
     *
     * 参数说明：
     * - notificationId：通知事件本地主键。
     * - event：已经持久化并补齐来源的通知事件。
     * - status：通知最终处理状态。
     * - reason：未形成交易候选的原因。
     */
    private suspend fun recordRawNotificationSafely(
        notificationId: Long,
        event: NotificationEvent,
        status: NotificationProcessStatus,
        reason: String,
    ) {
        runCatching {
            modelTrainingSampleRepository.recordRawNotification(
                notificationId = notificationId,
                event = event,
                status = status,
                reason = reason,
            )
        }
    }

    /**
     * 用途说明：
     * - 对过滤或短路结果统一落成功状态。
     *
     * 参数说明：
     * - notificationId：通知主键。
     * - destination：最终落点。
     * - status：处理状态。
     * - message：结果说明。
     * - retryCount：累计重试次数。
     * - screenshotRef：关联截图引用。
     */
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

/**
 * 用途说明：
 * - 默认启用的通知来源集合。
 */
private val DEFAULT_SOURCES = setOf(
    NotificationSourceApp.WECHAT,
    NotificationSourceApp.ALIPAY,
    NotificationSourceApp.BANK_SMS,
    NotificationSourceApp.UNKNOWN,
)

/**
 * 用途说明：
 * - 通知主链路的短时判重窗口。
 */
private const val DUPLICATE_WINDOW_MS = 30 * 1000L

/**
 * 用途说明：
 * - 分钟窗口结构。
 *
 * 参数说明：
 * - startInclusive：窗口起点。
 * - endInclusive：窗口终点。
 */
private data class MinuteWindow(
    val startInclusive: Long,
    val endInclusive: Long,
)

/**
 * 用途说明：
 * - 把毫秒时间戳归一化成分钟窗口。
 *
 * 参数说明：
 * - 接收者：原始毫秒时间戳。
 */
private fun Long.toMinuteWindow(): MinuteWindow {
    val startInclusive = this - (this % ONE_MINUTE_MILLIS)
    val endInclusive = startInclusive + ONE_MINUTE_MILLIS - 1
    return MinuteWindow(
        startInclusive = startInclusive,
        endInclusive = endInclusive,
    )
}

/**
 * 用途说明：
 * - 解析启用来源集合。
 *
 * 参数说明：
 * - sourceNames：DataStore 中保存的来源名称集合。
 */
internal fun resolveEnabledSources(sourceNames: Set<String>?): Set<NotificationSourceApp> {
    if (sourceNames == null) {
        return DEFAULT_SOURCES.toSet()
    }
    return sourceNames.mapNotNullTo(mutableSetOf()) { sourceName ->
        NotificationSourceApp.entries.firstOrNull { source -> source.name == sourceName }
    }
}

/**
 * 用途说明：
 * - 应用来源开关变更。
 *
 * 参数说明：
 * - currentSourceNames：当前已启用来源名称集合。
 * - sources：本次要切换的来源集合。
 * - enabled：目标状态。
 */
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

/**
 * 用途说明：
 * - 生成 CSV 安全文本。
 *
 * 参数说明：
 * - value：待转义的原始值。
 */
private fun csvEscape(value: Any?): String {
    val text = value?.toString().orEmpty()
    return "\"${text.replace("\"", "\"\"")}\""
}

/**
 * 用途说明：
 * - 对错误消息做最小脱敏。
 *
 * 参数说明：
 * - message：原始错误信息。
 */
private fun sanitize(message: String): String {
    return message.replace(Regex("""\d{6,}"""), "******")
}

/**
 * 用途说明：
 * - 解析月预算映射。
 *
 * 参数说明：
 * - rawValue：DataStore 中保存的原始字符串。
 */
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

/**
 * 用途说明：
 * - 序列化月预算映射。
 *
 * 参数说明：
 * - budgets：待序列化预算映射。
 */
private fun serializeMonthlyBudgets(budgets: Map<String, Double>): String {
    if (budgets.isEmpty()) return ""
    return budgets.toSortedMap().entries.joinToString(separator = ";") { (monthKey, amount) ->
        "$monthKey=${BUDGET_AMOUNT_FORMAT.format(amount)}"
    }
}

private val BUDGET_AMOUNT_FORMAT = DecimalFormat("0.##")
