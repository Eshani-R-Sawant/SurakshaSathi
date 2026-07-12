package com.sbi.surakshasathi.feature.ragwarning.domain.model

/**
 * Verdict returned by the external RAG agent for an analyzed message (§4b).
 */
enum class RagVerdict {
    PHISHING,
    SCAM,
    SAFE,
}

/**
 * Persona the RAG agent believes the user belongs to — drives which tone/
 * vocabulary the warning and guideline are written in.
 */
enum class RagPersona {
    FARMER,
    STUDENT,
    SENIOR,
    GENERAL,
}

/**
 * Domain result of analyzing a flagged message with the RAG agent.
 *
 * Domain layer ONLY — zero Android/framework imports. Produced by
 * [com.sbi.surakshasathi.feature.ragwarning.data.repository.RagRepositoryImpl]
 * (real backend) or its offline fallback, never fabricated on-device: the
 * RAG agent is an external, API-based service (§4b), not part of this client.
 */
data class RagWarning(
    /** Local message ID this warning was generated for. */
    val messageId: Long,
    val verdict: RagVerdict,
    /** Short, user-facing warning text, already localized by the RAG agent. */
    val warning: String,
    /** Step-by-step safe action, already localized by the RAG agent. */
    val guideline: String,
    /** BCP-47-ish language tag the response is written in (hi, mr, ta, en, ...). */
    val language: String,
    val persona: RagPersona,
    /** RAG agent's confidence in this verdict, 0.0–1.0. */
    val confidence: Float,
    /** True if this warning was produced by the offline fallback, not the live agent. */
    val isOfflineFallback: Boolean = false,
)

/**
 * A pre-emptive, dialect-specific warning pushed to a high-risk user segment
 * (§4b second endpoint: `GET /rag/segment-alerts`). Consumed by Flow 4a via
 * WorkManager + FCM — modeled here since it's part of the RAG contract.
 */
data class RagSegmentAlert(
    val region: String,
    val persona: RagPersona,
    val language: String,
    val title: String,
    val body: String,
)
