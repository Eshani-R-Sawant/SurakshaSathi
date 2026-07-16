package com.sbi.surakshasathi.feature.ragwarning.data.remote

import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.MessageScanResponseDto
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit contract for the external RAG agent (§4b) — pinned to Rag_model's
 * real, deployed route: `POST /v1/scan/message` in `api/routes/message_scan.py`.
 *
 * That route is `multipart/form-data`, not JSON (it also accepts an optional
 * file attachment for QR/APK scans, which this client doesn't send yet), so
 * every field rides as a `@Part` rather than a `@Body`.
 *
 * This service is deployed and owned separately from this Android client —
 * the client only talks to it over this endpoint, relative to
 * [com.sbi.surakshasathi.BuildConfig.BACKEND_BASE_URL].
 */
interface RagApi {
    /**
     * Scans a flagged message and returns the RAG agent's threat report
     * (verdict + risk score + a warning/action pair localized into [language]
     * by the backend).
     *
     * @param mlModelMetadata JSON-encoded object built from the on-device
     *   [com.sbi.surakshasathi.feature.messagescan.data.classifier.HybridDecisionEngine]
     *   decision — the backend folds this into its LLM prompt as supplementary,
     *   unvalidated context (see `message_scan.py`'s `ml_model_metadata` Form field).
     */
    @Multipart
    @POST("v1/scan/message")
    suspend fun scanMessage(
        @Part("message_id") messageId: RequestBody,
        @Part("original_message") originalMessage: RequestBody,
        @Part("language") language: RequestBody,
        @Part("ml_model_metadata") mlModelMetadata: RequestBody,
    ): MessageScanResponseDto
}
