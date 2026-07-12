package com.sbi.surakshasathi.feature.ragwarning.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.messagescan.data.mapper.IncomingMessageMapper
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.data.remote.RagApi
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagSegmentAlert
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import com.sbi.surakshasathi.feature.ragwarning.domain.repository.RagRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [RagRepository] — the real, always-default
 * data source (§1.5 constraint: Fake* is a fallback only, never the default).
 *
 * Resilience (§8D, offline-first): if the live call to the RAG agent fails
 * for any reason (no network, backend down, timeout, bad response) OR the
 * user has explicitly enabled the offline-fallback runtime flag, this falls
 * back to [FakeRagDataSource]'s canned localized copy rather than leaving the
 * user with no warning at all.
 */
@Singleton
class RagRepositoryImpl
    @Inject
    constructor(
        private val ragApi: RagApi,
        private val fakeRagDataSource: FakeRagDataSource,
        private val mapper: IncomingMessageMapper,
        private val preferences: UserPreferencesDataStore,
    ) : RagRepository {
        override suspend fun analyzeMessage(message: Message): Result<RagWarning> {
            val prefs = preferences.userPreferences.first()

            if (prefs.useOfflineFallback) {
                return Result.Success(fakeRagDataSource.analyze(message, prefs.selectedLanguage))
            }

            val liveResult =
                safeCall {
                    val envelope = mapper.toEnvelopeFromMessage(message)
                    ragApi.analyzeMessage(envelope).toDomain(messageId = message.id)
                }

            return when (liveResult) {
                is Result.Success -> liveResult
                is Result.Error -> {
                    Timber.w("RAG live analyze failed (${liveResult.error.message}) — using offline fallback")
                    Result.Success(fakeRagDataSource.analyze(message, prefs.selectedLanguage))
                }
                is Result.Loading -> liveResult
            }
        }

        override suspend fun getSegmentAlerts(
            region: String,
            persona: String,
        ): Result<List<RagSegmentAlert>> {
            val prefs = preferences.userPreferences.first()

            if (prefs.useOfflineFallback) {
                return Result.Success(fakeRagDataSource.segmentAlerts(region, persona, prefs.selectedLanguage))
            }

            val liveResult =
                safeCall {
                    ragApi.getSegmentAlerts(region, persona).map { it.toDomain() }
                }

            return when (liveResult) {
                is Result.Success -> liveResult
                is Result.Error -> {
                    Timber.w("RAG segment-alerts fetch failed (${liveResult.error.message}) — using offline fallback")
                    Result.Success(fakeRagDataSource.segmentAlerts(region, persona, prefs.selectedLanguage))
                }
                is Result.Loading -> liveResult
            }
        }
    }
