package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.NotificationSourceApp
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val ocrEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<NotificationSourceApp> = emptySet(),
    val exportPreview: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val exportPreview = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .combine(exportPreview) { settings, preview ->
            SettingsUiState(
                autoAccountingEnabled = settings.autoAccountingEnabled,
                cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
                captureEnabled = settings.captureEnabled,
                ocrEnabled = settings.ocrEnabled,
                notificationRetentionDays = settings.notificationRetentionDays,
                screenshotRetentionDays = settings.screenshotRetentionDays,
                enabledSources = settings.enabledSources,
                exportPreview = preview,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setAutoAccountingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoAccountingEnabled(enabled) }
    }

    fun setCloudAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloudAnalysisEnabled(enabled) }
    }

    fun setCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCaptureEnabled(enabled) }
    }

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOcrEnabled(enabled) }
    }

    fun setNotificationRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setNotificationRetentionDays(days) }
    }

    fun setScreenshotRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setScreenshotRetentionDays(days) }
    }

    fun setSourceEnabled(
        source: NotificationSourceApp,
        enabled: Boolean,
    ) {
        viewModelScope.launch { settingsRepository.setSourceEnabled(source, enabled) }
    }

    fun loadExportPreview() {
        viewModelScope.launch {
            exportPreview.value = ledgerRepository.exportCsv()
        }
    }
}
