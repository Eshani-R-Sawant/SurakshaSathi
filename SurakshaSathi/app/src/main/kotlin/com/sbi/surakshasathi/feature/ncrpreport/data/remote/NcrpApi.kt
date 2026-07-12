package com.sbi.surakshasathi.feature.ncrpreport.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit contract for the I4C/NCRP bridge service and the RAG training feed (§7b). */
interface NcrpApi {
    @POST("i4c/ncrp/report")
    suspend fun submitReport(
        @Body request: NcrpReportRequestDto,
    ): NcrpReportResponseDto

    /** Forwards an anonymized, persona-tagged record to the RAG training feed, quarantined until clustered (§7b). */
    @POST("rag/feed")
    suspend fun feedRagTraining(
        @Body request: RagFeedRequestDto,
    )
}

@Serializable
data class NcrpReportRequestDto(
    val apkSha256: String?,
    val installSource: String?,
    val deviceIntegrity: DeviceIntegrityDto,
    val offendingMessage: OffendingMessageDto?,
    val location: LocationDto?,
    val reportedAtMillis: Long,
    val reporterConsent: Boolean,
)

@Serializable
data class DeviceIntegrityDto(val status: String?, val rooted: Boolean)

@Serializable
data class OffendingMessageDto(val sender: String?, val body: String?, val urls: List<String>)

@Serializable
data class LocationDto(val region: String?, val lat: Double?, val lng: Double?)

@Serializable
data class NcrpReportResponseDto(val caseId: String, val status: String)

@Serializable
data class RagFeedRequestDto(
    val apkSha256: String?,
    val messageBody: String?,
    val region: String?,
    val quarantine: Boolean = true,
)
