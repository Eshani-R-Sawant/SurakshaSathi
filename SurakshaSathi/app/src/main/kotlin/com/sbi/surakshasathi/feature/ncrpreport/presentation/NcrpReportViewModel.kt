package com.sbi.surakshasathi.feature.ncrpreport.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import com.sbi.surakshasathi.feature.apkscan.domain.usecase.CheckDeviceIntegrityUseCase
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ForensicReport
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpCaseResult
import com.sbi.surakshasathi.feature.ncrpreport.domain.usecase.SubmitNcrpReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NcrpReportUiState {
    data object AwaitingConsent : NcrpReportUiState

    data object Submitting : NcrpReportUiState

    data class Submitted(val result: NcrpCaseResult) : NcrpReportUiState

    data class Error(val message: String) : NcrpReportUiState
}

/**
 * Backs the NCRP confirmation screen (§7b). [contextId] (from the route) is
 * either a message ID (numeric) or an APK package name — this resolves
 * whichever is relevant into a [ForensicReport], but the actual submission
 * only happens after [onConsentGiven] (explicit consent required before any
 * message body/location is uploaded, per §7b).
 *
 * NOTE: location is intentionally left blank in this build — collecting it
 * would require a separate location-permission flow this screen doesn't
 * request; the domain model supports it for when that's wired in.
 */
@HiltViewModel
class NcrpReportViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val apkScanRepository: ApkScanRepository,
        private val checkDeviceIntegrityUseCase: CheckDeviceIntegrityUseCase,
        private val submitNcrpReportUseCase: SubmitNcrpReportUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val contextId: String = savedStateHandle.get<String>("contextId").orEmpty()

        private val _uiState = MutableStateFlow<NcrpReportUiState>(NcrpReportUiState.AwaitingConsent)
        val uiState: StateFlow<NcrpReportUiState> = _uiState.asStateFlow()

        fun onConsentGiven() =
            viewModelScope.launch {
                _uiState.value = NcrpReportUiState.Submitting

                val integrity =
                    when (val result = checkDeviceIntegrityUseCase()) {
                        is Result.Success -> result.data
                        else -> null
                    }

                val report = buildReport(integrity?.status?.name, integrity?.isRooted ?: false)
                when (val result = submitNcrpReportUseCase(report)) {
                    is Result.Success -> _uiState.value = NcrpReportUiState.Submitted(result.data)
                    is Result.Error -> _uiState.value = NcrpReportUiState.Error(result.error.message ?: "Submission failed")
                    is Result.Loading -> Unit
                }
            }

        private suspend fun buildReport(
            integrityStatus: String?,
            rooted: Boolean,
        ): ForensicReport {
            val messageId = contextId.toLongOrNull()
            if (messageId != null) {
                val message = (messageRepository.getById(messageId) as? Result.Success)?.data
                return ForensicReport(
                    offendingSender = message?.sender,
                    offendingMessageBody = message?.body,
                    offendingUrls = message?.extractedUrls.orEmpty(),
                    deviceIntegrityStatus = integrityStatus,
                    deviceRooted = rooted,
                    reportedAtMillis = System.currentTimeMillis(),
                    reporterConsent = true,
                )
            }

            val apkResult = (apkScanRepository.getCachedResult(contextId) as? Result.Success)?.data
            return ForensicReport(
                apkSha256 = apkResult?.sha256,
                apkPackageName = apkResult?.packageName ?: contextId,
                deviceIntegrityStatus = integrityStatus,
                deviceRooted = rooted,
                reportedAtMillis = System.currentTimeMillis(),
                reporterConsent = true,
            )
        }
    }
