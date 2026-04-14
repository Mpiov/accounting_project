package com.autobook.cn.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.PermissionStatusChecker
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.feature.onboarding.OnboardingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 引导页 ViewModel，组合系统权限状态与本地设置状态。 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // 持续监听设置仓库，让引导页开关状态和持久化配置保持同步。
        settingsRepository.settings
            .onEach { settings ->
                applySettings(settings)
            }
            .launchIn(viewModelScope)
    }

    fun refreshPermissionStatus() {
        // 从系统设置页返回后，仅刷新权限状态，不覆写已有开关选择。
        applySettings(
            AppSettings(
                onboardingCompleted = _uiState.value.onboardingCompleted,
                autoAccountingEnabled = _uiState.value.autoAccountingEnabled,
                cloudAnalysisEnabled = _uiState.value.cloudAnalysisEnabled,
            ),
        )
    }

    fun setAutoAccountingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoAccountingEnabled(enabled)
        }
    }

    fun setCloudAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCloudAnalysisEnabled(enabled)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    private fun applySettings(settings: AppSettings) {
        _uiState.value = OnboardingUiState(
            notificationAccess = PermissionStatusChecker.hasNotificationAccess(context),
            accessibilityAccess = PermissionStatusChecker.hasAccessibilityAccess(
                context = context,
                serviceClassName = "com.autobook.cn.service.automation.AutobookAccessibilityService",
            ),
            autoAccountingEnabled = settings.autoAccountingEnabled,
            cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
            onboardingCompleted = settings.onboardingCompleted,
        )
    }
}
