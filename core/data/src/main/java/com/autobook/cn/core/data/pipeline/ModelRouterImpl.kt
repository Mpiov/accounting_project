package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.data.TransactionScoringMode
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.TransactionCandidate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 通知候选路由器。
 *
 * 用途说明：
 * - 通知监听后的自动记账主链路默认只走普通规则解析。
 * - 只有服务器确认当前用户具备会员机器学习能力时，才会启用本地机器学习置信度。
 *
 * 参数说明：
 * - localExtractor：从通知标题和正文中抽取交易候选的本地规则解析器。
 * - membershipRepository：读取服务器确认过的会员能力门禁。
 */
class ModelRouterImpl @Inject constructor(
    private val localExtractor: TransactionExtractor,
    private val membershipRepository: MembershipRepository,
) : ModelRouter {
    /**
     * 解析当前通知并返回符合会员门禁的本地结果。
     *
     * 参数说明：
     * - event：当前待路由的原始通知事件，包含包名、标题、正文和发布时间。
     */
    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
        val membership = membershipRepository.membershipStatus.first()
        val scoringMode = if (membership.canUseMachineLearning) {
            TransactionScoringMode.MEMBER_MACHINE_LEARNING
        } else {
            TransactionScoringMode.RULES_ONLY
        }
        return localExtractor.extract(
            event = event,
            scoringMode = scoringMode,
        )
    }
}
