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
import com.autobook.cn.core.database.entity.PendingStatsEntity
import com.autobook.cn.core.model.AppSettings
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
import com.autobook.cn.core.network.BackendCloudModelResponse
import com.autobook.cn.core.network.BackendCreateMembershipOrderRequest
import com.autobook.cn.core.network.BackendLoginRequest
import com.autobook.cn.core.network.BackendMembershipOrderResponse
import com.autobook.cn.core.network.BackendMembershipOrderStateResponse
import com.autobook.cn.core.network.BackendMembershipStatusResponse
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
        val current = pendingEntryDao.getStats()
        val nextCount = ((current?.activeCount ?: 0) + delta).coerceAtLeast(0)
        pendingEntryDao.upsertStats(PendingStatsEntity(activeCount = nextCount))
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
                    loggedInAt = System.currentTimeMillis(),
                    source = "server",
                )
                context.authDataStore.edit { preferences ->
                    // 登录态只保存服务器返回值，本地不伪造会员或模型权限。
                    preferences[Keys.userId] = session.userId
                    preferences[Keys.phone] = session.phone
                    preferences[Keys.accessToken] = session.accessToken
                    preferences[Keys.refreshToken] = session.refreshToken
                    preferences[Keys.accessTokenExpiresAt] = data.accessTokenExpiresAt
                    preferences[Keys.refreshTokenExpiresAt] = data.refreshTokenExpiresAt
                    preferences[Keys.loggedInAt] = session.loggedInAt
                }
                OperationResult.Success(session)
            }
            is OperationResult.RetryableFailure -> result
            is OperationResult.TerminalFailure -> result
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
            loggedInAt = preferences[Keys.loggedInAt] ?: 0L,
        )
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
 */
@Singleton
class MembershipRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendApi: AutobookBackendApi,
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
        val session = authRepository.authState.first().session
        val currentStatus = when {
            session == null -> loggedOutMembershipStatus()
            else -> when (
                val result = callBackend(MEMBERSHIP_STATUS_ENDPOINT) {
                    backendApi.membershipStatus(session.authorizationHeader())
                }
            ) {
                is OperationResult.Success -> result.data.data.toMembershipStatus(
                    fallbackSession = session,
                    serverTime = result.data.serverTime,
                )
                is OperationResult.RetryableFailure -> MembershipStatus(
                    userId = session.userId,
                    phone = session.phone,
                    serverVerified = false,
                    refreshedAt = System.currentTimeMillis(),
                    source = "server-error",
                    message = result.message,
                )
                is OperationResult.TerminalFailure -> MembershipStatus(
                    userId = session.userId,
                    phone = session.phone,
                    serverVerified = false,
                    refreshedAt = System.currentTimeMillis(),
                    source = "server-rejected",
                    message = result.message,
                )
            }
        }
        refreshedStatus.value = currentStatus
        return currentStatus
    }

    /**
     * 用途说明：
     * - 构造未登录时的免费状态，避免任何本地缓存误开会员能力。
     */
    private fun loggedOutMembershipStatus(): MembershipStatus {
        return MembershipStatus(message = "未登录，会员状态需要邮箱登录后从服务器确认。")
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
        val session = authRepository.authState.first().session
            ?: return OperationResult.TerminalFailure("请先使用邮箱登录/注册，再开通会员")
        return when (
            val result = callBackend(MEMBERSHIP_ORDER_CREATE_ENDPOINT) {
                backendApi.createMembershipOrder(
                    authorization = session.authorizationHeader(),
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
        val session = authRepository.authState.first().session
            ?: return OperationResult.TerminalFailure("请先使用邮箱登录/注册，再查询会员订单")
        return when (
            val result = callBackend(MEMBERSHIP_ORDER_QUERY_ENDPOINT) {
                backendApi.queryMembershipOrder(
                    authorization = session.authorizationHeader(),
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
 * - 云端学习仓储实现。
 * - 负责会员门禁、设置开关、本地样本导出、全局样本池上传和全局模型拉取。
 *
 * 参数说明：
 * - context：应用级上下文，用于生成设备与版本信息。
 * - settingsRepository：读取和写入云端学习开关。
 * - authRepository：读取当前邮箱登录会话；内部会话字段名暂沿用 `phone`。
 * - membershipRepository：判断当前用户是否具备会员能力。
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
        val effectiveEnabled = settings.cloudLearningEnabled && membership.canUseCloudLearning
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
                effectiveEnabled -> "会员全局云端学习已开启，会上传通知原文贡献全局模型，并拉取服务器最新全局模型。"
                membership.canUseMachineLearning -> "会员机器学习已开启；云端学习关闭时不上传原文，仍可拉取全局模型。"
                !membership.serverVerified -> membership.message
                else -> "免费版仅使用普通规则算法，开通会员后可启用机器学习。"
            },
        )
    }

    /**
     * 用途说明：
     * - 写入会员全局云端学习开关，是否真正生效仍由会员状态共同决定。
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
     * - 请求体会包含通知原文，因此只有服务器确认会员且云学习开关生效时才发送到全局样本池。
     */
    override suspend fun uploadTrainingSamples(): OperationResult<Unit> {
        val session = authRepository.authState.first().session
            ?: return OperationResult.TerminalFailure("请先使用邮箱登录/注册，再上传训练样本")
        val membership = membershipRepository.refresh()
        val settings = settingsRepository.settings.first()
        if (!membership.canUseCloudLearning || !settings.cloudLearningEnabled) {
            return OperationResult.TerminalFailure("当前未启用会员全局云端学习，不会上传通知原文")
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
            val result = callBackend(TRAINING_SAMPLE_UPLOAD_ENDPOINT) {
                backendApi.uploadTrainingSamples(
                    authorization = session.authorizationHeader(),
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
        val session = authRepository.authState.first().session
            ?: return OperationResult.TerminalFailure("请先使用邮箱登录/注册，再拉取全局模型")
        val membership = membershipRepository.refresh()
        if (!membership.canUseMachineLearning) {
            return OperationResult.TerminalFailure("当前账号不是会员或未开启机器学习，继续使用普通规则算法")
        }
        val currentModelVersion = cloudModelStore.getActiveModel()?.modelVersion ?: syncSnapshot.value.modelVersion
        return when (
            val result = callBackend(GLOBAL_MODEL_ENDPOINT) {
                backendApi.pullGlobalModel(
                    authorization = session.authorizationHeader(),
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
        plan = runCatching { MembershipPlan.valueOf(plan) }.getOrDefault(MembershipPlan.MONTHLY),
        amountCents = amountCents,
        appId = params?.appId.orEmpty(),
        partnerId = params?.partnerId.orEmpty(),
        prepayId = params?.prepayId.orEmpty(),
        packageValue = params?.packageValue.orEmpty(),
        nonceStr = params?.nonceStr.orEmpty(),
        timestamp = params?.timestamp.orEmpty(),
        sign = params?.sign.orEmpty(),
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

/** 用途说明：查询服务器权威会员状态的后端接口路径。 */
private const val MEMBERSHIP_STATUS_ENDPOINT = "GET /v1/membership/status"

/** 用途说明：创建会员支付订单的后端接口路径。 */
private const val MEMBERSHIP_ORDER_CREATE_ENDPOINT = "POST /v1/payments/membership-orders"

/** 用途说明：查询会员支付订单的后端接口路径。 */
private const val MEMBERSHIP_ORDER_QUERY_ENDPOINT = "GET /v1/payments/membership-orders/{orderId}"

/** 用途说明：上传会员全局云端学习训练样本到全局样本池的后端接口路径。 */
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
        val cloudAnalysisEnabled = booleanPreferencesKey("cloud_analysis_enabled")
        val cloudLearningEnabled = booleanPreferencesKey("cloud_learning_enabled")
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
            cloudAnalysisEnabled = preferences[Keys.cloudAnalysisEnabled] ?: true,
            cloudLearningEnabled = preferences[Keys.cloudLearningEnabled] ?: true,
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
        context.settingsDataStore.edit { it[Keys.autoAccountingEnabled] = enabled }
    }

    override suspend fun setCloudAnalysisEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.cloudAnalysisEnabled] = enabled }
    }

    override suspend fun setCloudLearningEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.cloudLearningEnabled] = enabled }
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
) : NotificationIngestor {
    override suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome> {
        val enriched = event.enrichForProcessing()
        val id = notificationEventDao.insert(enriched.toEntity())
        val persisted = enriched.copy(id = id)
        val settings = settingsRepository.settings.first()
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
