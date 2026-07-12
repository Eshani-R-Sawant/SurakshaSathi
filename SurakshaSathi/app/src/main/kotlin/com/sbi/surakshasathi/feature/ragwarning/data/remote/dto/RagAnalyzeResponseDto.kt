package com.sbi.surakshasathi.feature.ragwarning.data.remote.dto

import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagPersona
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagSegmentAlert
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagVerdict
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import kotlinx.serialization.Serializable

/**
 * Wire shape of `POST /rag/analyze`'s response (§4b):
 * ```json
 * {
 *   "verdict": "PHISHING | SCAM | SAFE",
 *   "warning": "...",
 *   "guideline": "...",
 *   "language": "hi | mr | ta | en | ...",
 *   "persona": "FARMER | STUDENT | SENIOR | GENERAL",
 *   "confidence": 0.0
 * }
 * ```
 */
@Serializable
data class RagAnalyzeResponseDto(
    val verdict: String,
    val warning: String,
    val guideline: String,
    val language: String,
    val persona: String,
    val confidence: Float,
) {
    /** Maps the wire DTO to the domain model, attaching the local [messageId]. */
    fun toDomain(
        messageId: Long,
        isOfflineFallback: Boolean = false,
    ): RagWarning =
        RagWarning(
            messageId = messageId,
            verdict = runCatching { RagVerdict.valueOf(verdict.uppercase()) }.getOrDefault(RagVerdict.SCAM),
            warning = warning,
            guideline = guideline,
            language = language,
            persona = runCatching { RagPersona.valueOf(persona.uppercase()) }.getOrDefault(RagPersona.GENERAL),
            confidence = confidence.coerceIn(0f, 1f),
            isOfflineFallback = isOfflineFallback,
        )
}

@Serializable
data class RagSegmentAlertDto(
    val region: String,
    val persona: String,
    val language: String,
    val title: String,
    val body: String,
) {
    fun toDomain(): RagSegmentAlert =
        RagSegmentAlert(
            region = region,
            persona = runCatching { RagPersona.valueOf(persona.uppercase()) }.getOrDefault(RagPersona.GENERAL),
            language = language,
            title = title,
            body = body,
        )
}
