package com.autobook.cn.app.navigation

import com.autobook.cn.core.model.MoneyDirection
import java.time.YearMonth

/**
 * 用途说明：
 * - 应用内所有导航目的地的统一定义。
 */
sealed class AutobookDestination(val route: String) {
    data object Onboarding : AutobookDestination("onboarding")

    /** 主壳层负责承载一级页面与统一 FAB。 */
    data object MainShell : AutobookDestination("main")

    data object MainOverview : AutobookDestination("main/overview")

    data object MainLedger : AutobookDestination("main/ledger")

    data object MainCharts : AutobookDestination("main/charts")

    data object MainPending : AutobookDestination("main/pending")

    data object MainSettings : AutobookDestination("main/settings")

    data object BudgetSettings : AutobookDestination("budget/settings")

    data object RecordsAll : AutobookDestination("records/all")

    /**
     * 用途说明：
     * - 月份详情页，负责展示某个月的日历、搜索与流水账。
     */
    data object LedgerMonthDetail : AutobookDestination("ledger/month?yearMonth={yearMonth}") {
        const val BASE_ROUTE: String = "ledger/month"

        /**
         * 用途说明：
         * - 生成月份详情页路由。
         *
         * 参数说明：
         * - yearMonth：目标年月。
         */
        fun createRoute(yearMonth: YearMonth): String = "$BASE_ROUTE?yearMonth=$yearMonth"
    }

    /**
     * 用途说明：
     * - 新建记账页支持携带默认收支方向与模板参数。
     *
     * 参数说明：
     * - direction：快速记账面板传入的默认方向。
     * - templateId：快速动作直接套用的模板标识。
     */
    data object EntryCreate : AutobookDestination("entry/create?direction={direction}&templateId={templateId}") {
        const val BASE_ROUTE: String = "entry/create"

        fun createRoute(
            direction: MoneyDirection? = null,
            templateId: String? = null,
        ): String {
            val queryParameters = buildList {
                direction?.let { add("direction=${it.name}") }
                templateId?.takeIf { it.isNotBlank() }?.let { add("templateId=$it") }
            }
            return if (queryParameters.isEmpty()) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?${queryParameters.joinToString("&")}"
            }
        }
    }

    data object EntryEdit : AutobookDestination("entry/edit/{ledgerId}") {
        /**
         * 用途说明：
         * - 生成编辑已有账单的路由。
         *
         * 参数说明：
         * - ledgerId：账单主键 id。
         */
        fun createRoute(ledgerId: Long): String = "entry/edit/$ledgerId"
    }

    data object PendingResolve : AutobookDestination("pending/resolve/{pendingId}") {
        /**
         * 用途说明：
         * - 生成待确认记录处理页路由。
         *
         * 参数说明：
         * - pendingId：待确认记录主键 id。
         */
        fun createRoute(pendingId: Long): String = "pending/resolve/$pendingId"
    }
}
