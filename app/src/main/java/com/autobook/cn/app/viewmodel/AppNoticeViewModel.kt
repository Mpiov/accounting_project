package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.model.MembershipStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * 用途说明：
 * - 主壳层专用提示 ViewModel，负责把全局提示事件暴露给 Snackbar。
 * - App 每次回到前台时检查免费自动记账体验是否已经用完，并主动提醒用户。
 *
 * 参数说明：
 * - membershipRepository：会员状态仓储，用于从服务器刷新免费体验次数。
 * - appNoticeBus：全局提示事件通道。
 */
@HiltViewModel
class AppNoticeViewModel @Inject constructor(
    private val membershipRepository: MembershipRepository,
    private val appNoticeBus: AppNoticeBus,
) : ViewModel() {
    /** 用途说明：壳层直接收集该事件流并展示底部提示条。 */
    val notices: SharedFlow<String> = appNoticeBus.notices

    /**
     * 用途说明：
     * - App 进入前台时执行一次服务器会员状态检查。
     * - 如果登录用户仍是免费用户且免费自动记账体验已用完，则每次进入 App 都提示。
     */
    fun onAppEnteredForeground() {
        viewModelScope.launch {
            val status = membershipRepository.refresh()
            if (status.shouldShowTrialExhaustedNotice()) {
                appNoticeBus.show(status.toTrialExhaustedNoticeMessage())
            }
        }
    }
}

/**
 * 用途说明：
 * - 判断当前会员状态是否需要展示免费体验耗尽提示。
 *
 * 参数说明：
 * - 接收者：服务器刷新后的会员状态。
 */
private fun MembershipStatus.shouldShowTrialExhaustedNotice(): Boolean {
    return hasLoggedInAccount && !isMember && autoLedgerTrialRemaining <= 0
}

/**
 * 用途说明：
 * - 生成免费自动记账体验耗尽时的统一底部提示文案。
 *
 * 参数说明：
 * - 接收者：服务器刷新后的会员状态，体验总次数来自 `autoLedgerTrialLimit`，不在本地写死。
 */
private fun MembershipStatus.toTrialExhaustedNoticeMessage(): String {
    val trialLimitText = autoLedgerTrialLimit.coerceAtLeast(0)
    // 提示里的次数使用服务器下发值，后续后台调整额度时客户端文案自动跟随。
    return "${trialLimitText} 次免费自动记账体验已用完，开通会员后可继续自动入账。"
}
