package com.sbi.surakshasathi.feature.frauddashboard.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.FraudCluster
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LoadFraudHeatmapUseCase
    @Inject
    constructor(
        private val repository: FraudReportRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(): Result<List<FraudCluster>> = withContext(dispatchers.io) { repository.getAggregatedFraudClusters() }
    }
