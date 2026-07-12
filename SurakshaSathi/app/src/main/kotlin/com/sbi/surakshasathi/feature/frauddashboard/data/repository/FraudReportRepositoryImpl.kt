package com.sbi.surakshasathi.feature.frauddashboard.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.FraudApi
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.FraudClusterDto
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.SegmentAlertRequestDto
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.FraudCluster
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.UserSegment
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Retrofit client is the default (§7). If the aggregate endpoint is
 * unreachable, falls back to realistic bundled sample clusters across Indian
 * regions so the map ALWAYS renders for the demo (§7 "offline fallback ships
 * realistic sample clusters... so the map always renders") — this is
 * explicitly an offline-resilience fallback, not the shipping default.
 */
@Singleton
class FraudReportRepositoryImpl
    @Inject
    constructor(
        private val fraudApi: FraudApi,
    ) : FraudReportRepository {
        override suspend fun getAggregatedFraudClusters(): Result<List<FraudCluster>> {
            val live = safeCall { fraudApi.getAggregatedFraud().map { it.toDomain() } }
            return when (live) {
                is Result.Success -> live
                is Result.Error -> {
                    Timber.w("Fraud aggregate fetch failed (${live.error.message}) — using bundled sample clusters")
                    Result.Success(SAMPLE_CLUSTERS)
                }
                is Result.Loading -> live
            }
        }

        override suspend fun pushSegmentAlert(
            segment: UserSegment,
            message: String,
        ): Result<Unit> =
            safeCall {
                fraudApi.pushSegmentAlert(
                    SegmentAlertRequestDto(
                        region = segment.region,
                        persona = segment.persona,
                        language = segment.language,
                        message = message,
                    ),
                )
            }

        private fun FraudClusterDto.toDomain() =
            FraudCluster(
                lat = lat,
                lng = lng,
                weight = weight,
                persona = persona,
                region = region,
                campaignTag = campaignTag,
            )

        companion object {
            /** Bundled offline fallback — realistic-looking clusters spread across major Indian regions. */
            val SAMPLE_CLUSTERS =
                listOf(
                    FraudCluster(19.0760, 72.8777, 0.9f, "GENERAL", "Mumbai", "fake_yono_apk"),
                    FraudCluster(28.6139, 77.2090, 0.8f, "STUDENT", "Delhi NCR", "otp_phishing_sms"),
                    FraudCluster(21.1458, 79.0882, 0.7f, "FARMER", "Vidarbha", "kyc_update_scam"),
                    FraudCluster(13.0827, 80.2707, 0.6f, "STUDENT", "Chennai", "reward_scam_whatsapp"),
                    FraudCluster(12.9716, 77.5946, 0.75f, "GENERAL", "Bengaluru", "fake_yono_apk"),
                    FraudCluster(22.5726, 88.3639, 0.5f, "SENIOR", "Kolkata", "account_blocked_sms"),
                    FraudCluster(23.0225, 72.5714, 0.65f, "FARMER", "Ahmedabad", "kyc_update_scam"),
                    FraudCluster(26.9124, 75.7873, 0.55f, "GENERAL", "Jaipur", "otp_phishing_sms"),
                    FraudCluster(17.3850, 78.4867, 0.6f, "STUDENT", "Hyderabad", "reward_scam_whatsapp"),
                    FraudCluster(25.5941, 85.1376, 0.7f, "FARMER", "Patna", "fake_yono_apk"),
                )
        }
    }
