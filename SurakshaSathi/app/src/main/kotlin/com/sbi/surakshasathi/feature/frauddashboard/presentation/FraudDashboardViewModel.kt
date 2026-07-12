package com.sbi.surakshasathi.feature.frauddashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.FraudCluster
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.UserSegment
import com.sbi.surakshasathi.feature.frauddashboard.domain.usecase.LoadFraudHeatmapUseCase
import com.sbi.surakshasathi.feature.frauddashboard.domain.usecase.PushSegmentAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FraudDashboardUiState {
    data object Loading : FraudDashboardUiState

    data class Success(val clusters: List<FraudCluster>) : FraudDashboardUiState
}

sealed interface PushAlertState {
    data object Idle : PushAlertState

    data object Sending : PushAlertState

    data object Sent : PushAlertState

    data class Failed(val message: String) : PushAlertState
}

/** Backs Flow 4a's heatmap + campaign panel. */
@HiltViewModel
class FraudDashboardViewModel
    @Inject
    constructor(
        private val loadFraudHeatmapUseCase: LoadFraudHeatmapUseCase,
        private val pushSegmentAlertUseCase: PushSegmentAlertUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FraudDashboardUiState>(FraudDashboardUiState.Loading)
        val uiState: StateFlow<FraudDashboardUiState> = _uiState.asStateFlow()

        private val _pushAlertState = MutableStateFlow<PushAlertState>(PushAlertState.Idle)
        val pushAlertState: StateFlow<PushAlertState> = _pushAlertState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() =
            viewModelScope.launch {
                _uiState.value = FraudDashboardUiState.Loading
                when (val result = loadFraudHeatmapUseCase()) {
                    is Result.Success -> _uiState.value = FraudDashboardUiState.Success(result.data)
                    is Result.Error -> _uiState.value = FraudDashboardUiState.Success(emptyList())
                    is Result.Loading -> Unit
                }
            }

        fun pushAlert(
            segment: UserSegment,
            message: String,
        ) = viewModelScope.launch {
            _pushAlertState.value = PushAlertState.Sending
            when (val result = pushSegmentAlertUseCase(segment, message)) {
                is Result.Success -> _pushAlertState.value = PushAlertState.Sent
                is Result.Error -> _pushAlertState.value = PushAlertState.Failed(result.error.message ?: "Push failed")
                is Result.Loading -> Unit
            }
        }

        fun dismissPushState() {
            _pushAlertState.value = PushAlertState.Idle
        }
    }
