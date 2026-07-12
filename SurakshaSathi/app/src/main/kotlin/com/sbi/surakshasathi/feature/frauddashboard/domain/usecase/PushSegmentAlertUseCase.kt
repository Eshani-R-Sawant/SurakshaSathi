package com.sbi.surakshasathi.feature.frauddashboard.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.UserSegment
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Operator-facing action (§7): push a vernacular alert to a targeted segment in one tap. */
class PushSegmentAlertUseCase
    @Inject
    constructor(
        private val repository: FraudReportRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(
            segment: UserSegment,
            message: String,
        ): Result<Unit> = withContext(dispatchers.io) { repository.pushSegmentAlert(segment, message) }
    }
