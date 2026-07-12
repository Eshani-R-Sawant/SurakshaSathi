package com.sbi.surakshasathi.feature.ragwarning.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagSegmentAlert
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning

/**
 * Contract for the external, API-based RAG agent (§4b).
 *
 * The RAG agent is NOT on-device — it is a separately deployed backend
 * service. This interface is the client's only touchpoint with it. The real
 * implementation is [com.sbi.surakshasathi.feature.ragwarning.data.repository.RagRepositoryImpl]
 * (Retrofit); an offline canned-response fallback is used only when the
 * network call fails or the runtime offline flag is set (§8D — offline-first,
 * never a shipping-default shortcut).
 */
interface RagRepository {
    /**
     * Sends [message]'s canonical envelope to `POST /rag/analyze` and returns
     * the RAG agent's verdict + localized warning/guideline.
     */
    suspend fun analyzeMessage(message: Message): Result<RagWarning>

    /**
     * Fetches pre-emptive, dialect-specific segment alerts for a region/persona
     * from `GET /rag/segment-alerts` (feeds Flow 4a's WorkManager + FCM push).
     */
    suspend fun getSegmentAlerts(
        region: String,
        persona: String,
    ): Result<List<RagSegmentAlert>>
}
