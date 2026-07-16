package com.sbi.surakshasathi.feature.ragwarning.data.remote.dto

import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagPersona
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagVerdict
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of `POST /v1/scan/message`'s response — pinned field-for-field to
 * Rag_model's `api/routes/message_scan.py:MessageScanResponse` /
 * `common/schemas.py:ThreatReport` (snake_case, the FastAPI/pydantic default;
 * kotlinx.serialization does not auto-convert casing). `technical_evidence` is
 * intentionally not modeled here — nothing in this client consumes it yet and
 * `ignoreUnknownKeys = true` (see core/di/NetworkModule) lets it pass through
 * unparsed rather than needing a placeholder DTO.
 */
@Serializable
data class MessageScanResponseDto(
    val report: ThreatReportDto,
    @SerialName("latency_ms") val latencyMs: Int,
)

@Serializable
data class ThreatReportDto(
    /** "BLOCK" | "QUARANTINE" | "ALLOW" */
    val verdict: String,
    @SerialName("threat_type") val threatType: String,
    @SerialName("risk_score") val riskScore: Float,
    @SerialName("suspicious_signals") val suspiciousSignals: List<String> = emptyList(),
    @SerialName("plain_language_explanation") val plainLanguageExplanation: String,
    @SerialName("recommended_action") val recommendedAction: String,
    /** Set by the backend's post-generation localization pass (api/routes/message_scan.py
     * `_localize_report`) to the language actually used for the two text fields above. */
    val language: String = "en",
) {
    /** Maps the wire DTO to the domain model, attaching the local [messageId]. */
    fun toDomain(
        messageId: Long,
        isOfflineFallback: Boolean = false,
    ): RagWarning =
        RagWarning(
            messageId = messageId,
            verdict =
                when (verdict.uppercase()) {
                    "BLOCK" -> RagVerdict.PHISHING
                    "QUARANTINE" -> RagVerdict.SCAM
                    else -> RagVerdict.SAFE
                },
            warning = plainLanguageExplanation,
            guideline = recommendedAction,
            language = language,
            persona = RagPersona.GENERAL,
            confidence = (riskScore / 100f).coerceIn(0f, 1f),
            threatType = threatType,
            suspiciousSignals = suspiciousSignals,
            isOfflineFallback = isOfflineFallback,
        )
}
