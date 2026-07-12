package com.sbi.surakshasathi.feature.frauddashboard.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FraudApi {
    @GET("fraud/aggregate")
    suspend fun getAggregatedFraud(): List<FraudClusterDto>

    @POST("alerts/segment")
    suspend fun pushSegmentAlert(
        @Body request: SegmentAlertRequestDto,
    )
}

@Serializable
data class FraudClusterDto(
    val lat: Double,
    val lng: Double,
    val weight: Float,
    val persona: String,
    val region: String,
    val campaignTag: String,
)

@Serializable
data class SegmentAlertRequestDto(
    val region: String,
    val persona: String,
    val language: String,
    val message: String,
)
