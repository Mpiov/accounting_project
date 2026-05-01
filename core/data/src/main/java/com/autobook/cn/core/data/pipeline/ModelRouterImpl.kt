package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.TransactionCandidate
import javax.inject.Inject

/**
 * 通知候选路由器。
 *
 * 用途说明：
 * - 通知监听后的自动记账主链路只走本地规则解析。
 * - 本类保留路由抽象，是为了让处理流水线不用感知具体解析实现，后续如果增加新策略也能集中改这里。
 *
 * 参数说明：
 * - localExtractor：从通知标题和正文中抽取交易候选的本地规则解析器。
 */
class ModelRouterImpl @Inject constructor(
    private val localExtractor: TransactionExtractor,
) : ModelRouter {
    /**
     * 解析当前通知并返回本地规则结果。
     *
     * 参数说明：
     * - event：当前待路由的原始通知事件，包含包名、标题、正文和发布时间。
     */
    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
        return localExtractor.extract(event)
    }
}
