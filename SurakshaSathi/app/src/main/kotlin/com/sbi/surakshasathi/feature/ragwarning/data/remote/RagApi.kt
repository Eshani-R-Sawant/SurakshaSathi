package com.sbi.surakshasathi.feature.ragwarning.data.remote

import com.sbi.surakshasathi.core.network.dto.MessageEnvelope
import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.RagAnalyzeResponseDto
import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.RagSegmentAlertDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit contract for the external RAG agent (§4b).
 *
 * This service is deployed and owned separately from this Android client —
 * the client only talks to it over these two endpoints, relative to
 * [com.sbi.surakshasathi.BuildConfig.BACKEND_BASE_URL].
 */
interface RagApi {
    /**
     * Analyzes a flagged message's canonical envelope and returns a
     * localized verdict + warning + guideline.
     */
    @POST("rag/analyze")
    suspend fun analyzeMessage(
        @Body envelope: MessageEnvelope,
    ): RagAnalyzeResponseDto

    /**
     * Pre-emptive, dialect-specific warnings clustered by region/persona —
     * feeds Flow 4a's segment-alert push (WorkManager + FCM).
     */
    @GET("rag/segment-alerts")
    suspend fun getSegmentAlerts(
        @Query("region") region: String,
        @Query("persona") persona: String,
    ): List<RagSegmentAlertDto>
}
