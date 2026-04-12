package com.autobook.cn.app.navigation

sealed class AutobookDestination(val route: String) {
    data object Onboarding : AutobookDestination("onboarding")
    data object Ledger : AutobookDestination("ledger")
    data object Pending : AutobookDestination("pending")
}
