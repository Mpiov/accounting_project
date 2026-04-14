package com.autobook.cn.app.navigation

import com.autobook.cn.core.model.MoneyDirection

/** 应用内所有导航目的地的统一定义。 */
sealed class AutobookDestination(val route: String) {
    data object Onboarding : AutobookDestination("onboarding")

    /** 主壳层只负责承载四个一级页面和统一的 FAB。 */
    data object MainShell : AutobookDestination("main")

    data object MainOverview : AutobookDestination("main/overview")

    data object MainLedger : AutobookDestination("main/ledger")

    data object MainCharts : AutobookDestination("main/charts")

    data object MainPending : AutobookDestination("main/pending")

    data object MainSettings : AutobookDestination("main/settings")

    data object BudgetSettings : AutobookDestination("budget/settings")

    data object RecordsAll : AutobookDestination("records/all")

    data object SearchResults : AutobookDestination("records/search?query={query}") {
        const val BASE_ROUTE: String = "records/search"

        /**
         * 生成搜索结果页路由。
         *
         * 参数说明：
         * - query：用户确认搜索后的关键词。
         */
        fun createRoute(query: String): String {
            val encodedQuery = android.net.Uri.encode(query)
            return "$BASE_ROUTE?query=$encodedQuery"
        }
    }

    /**
     * 新建记账页支持携带默认收支方向和模板参数。
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
        fun createRoute(ledgerId: Long): String = "entry/edit/$ledgerId"
    }

    data object PendingResolve : AutobookDestination("pending/resolve/{pendingId}") {
        fun createRoute(pendingId: Long): String = "pending/resolve/$pendingId"
    }
}
