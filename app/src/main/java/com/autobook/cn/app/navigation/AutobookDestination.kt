package com.autobook.cn.app.navigation

/** 应用内所有导航目的地的统一定义。 */
sealed class AutobookDestination(val route: String) {
    data object Onboarding : AutobookDestination("onboarding")
    data object Ledger : AutobookDestination("ledger")
    data object Pending : AutobookDestination("pending")
    data object Settings : AutobookDestination("settings")
    data object EntryCreate : AutobookDestination("entry/create")
    data object EntryEdit : AutobookDestination("entry/edit/{ledgerId}") {
        fun createRoute(ledgerId: Long): String = "entry/edit/$ledgerId"
    }

    data object PendingResolve : AutobookDestination("pending/resolve/{pendingId}") {
        fun createRoute(pendingId: Long): String = "pending/resolve/$pendingId"
    }
}
