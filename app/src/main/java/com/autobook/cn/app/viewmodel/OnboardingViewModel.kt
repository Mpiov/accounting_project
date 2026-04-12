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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        init {
            settingsRepository.settings
                .onEach { settings ->
                    applySettings(settings)
                }
                .launchIn(viewModelScope)
        }

        fun refreshPermissionStatus() {
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
            _uiState.value =
                OnboardingUiState(
                    notificationAccess = PermissionStatusChecker.hasNotificationAccess(context),
                    accessibilityAccess =
                        PermissionStatusChecker.hasAccessibilityAccess(
                            context = context,
                            serviceClassName = "com.autobook.cn.service.automation.AutobookAccessibilityService",
                        ),
                    autoAccountingEnabled = settings.autoAccountingEnabled,
                    cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
                    onboardingCompleted = settings.onboardingCompleted,
                )
        }
    }
