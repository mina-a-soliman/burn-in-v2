package com.burnsubtitle.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burnsubtitle.data.maintenance.AppMaintenanceManager
import com.burnsubtitle.data.maintenance.MaintenanceStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isClearing: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val lastStats: MaintenanceStats? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val maintenanceManager: AppMaintenanceManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun clearCacheAndResetFonts() {
        if (_uiState.value.isClearing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true, successMessage = null, errorMessage = null) }

            val result = maintenanceManager.clearCacheAndResetFonts()

            result.fold(
                onSuccess = { stats ->
                    val freedMb = String.format("%.2f MB", stats.bytesFreed / (1024.0 * 1024.0))
                    _uiState.update {
                        it.copy(
                            isClearing = false,
                            lastStats = stats,
                            successMessage = "Cleaned $freedMb cache and restored ${stats.fontsRestored} font(s).",
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isClearing = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to reset cache and fonts.",
                        )
                    }
                },
            )
        }
    }

    fun onSnackbarDismissed() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
