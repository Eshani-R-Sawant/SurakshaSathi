package com.sbi.surakshasathi.feature.frauddashboard.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.FraudCluster
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.UserSegment

/** Fraud heatmap + segment-alert push (§7). */
interface FraudReportRepository {
    suspend fun getAggregatedFraudClusters(): Result<List<FraudCluster>>

    /** Dispatches a vernacular alert to [segment] — the backend fans it out via FCM topic. */
    suspend fun pushSegmentAlert(
        segment: UserSegment,
        message: String,
    ): Result<Unit>
}
