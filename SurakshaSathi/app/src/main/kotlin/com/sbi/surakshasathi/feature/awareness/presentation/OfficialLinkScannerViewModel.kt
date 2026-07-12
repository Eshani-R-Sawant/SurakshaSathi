package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkCheckResult
import com.sbi.surakshasathi.feature.awareness.domain.usecase.VerifyOfficialLinkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScannerUiState {
    data object Scanning : ScannerUiState

    data object Verifying : ScannerUiState

    data class Result(val result: OfficialLinkCheckResult) : ScannerUiState
}

/** Backs the "Is this the real SBI app?" scanner (§7c 5.1). */
@HiltViewModel
class OfficialLinkScannerViewModel
    @Inject
    constructor(
        private val verifyOfficialLinkUseCase: VerifyOfficialLinkUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Scanning)
        val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

        private var busy = false

        /** Called for every decoded QR value; ignores subsequent scans while one is being verified/shown. */
        fun onCodeScanned(content: String) {
            if (busy) return
            busy = true
            _uiState.value = ScannerUiState.Verifying
            viewModelScope.launch {
                when (val result = verifyOfficialLinkUseCase(content)) {
                    is Result.Success -> _uiState.value = ScannerUiState.Result(result.data)
                    is Result.Error -> _uiState.value = ScannerUiState.Scanning.also { busy = false }
                    is Result.Loading -> Unit
                }
            }
        }

        fun onCheckManualInput(content: String) = onCodeScanned(content)

        fun scanAgain() {
            busy = false
            _uiState.value = ScannerUiState.Scanning
        }
    }
