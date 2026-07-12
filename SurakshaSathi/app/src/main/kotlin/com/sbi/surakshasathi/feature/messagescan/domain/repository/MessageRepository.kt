package com.sbi.surakshasathi.feature.messagescan.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import kotlinx.coroutines.flow.Flow

/**
 * Contract for message persistence and classification.
 *
 * Layer rule: this interface lives in DOMAIN — zero Android imports.
 * Implementation lives in DATA ([com.sbi.surakshasathi.feature.messagescan.data.repository.MessageRepositoryImpl]).
 *
 * All operations return [Result] — never throw. ViewModels map errors to UiState.
 */
interface MessageRepository {
    /**
     * Observes all stored messages as a live [Flow].
     * Room emits a new list whenever the table changes.
     * Presentation layer pages this list — do NOT load all at once.
     */
    fun observeMessages(): Flow<List<Message>>

    /**
     * Observes only flagged messages (SUSPICIOUS or MALICIOUS).
     * Drives the Alerts tab badge count.
     */
    fun observeFlaggedMessages(): Flow<List<Message>>

    /**
     * Classifies [raw], persists the resulting [Message], and returns it.
     * Classification is hybrid: on-device TFLite + rule engine.
     * If SUSPICIOUS/MALICIOUS, the caller (use case) hands off to RAG.
     *
     * Performance target: < 50 ms end-to-end (§8A).
     */
    suspend fun classifyAndStore(raw: RawIncomingMessage): Result<Message>

    /** Retrieve a single message by ID. */
    suspend fun getById(id: Long): Result<Message>

    /**
     * Mark a message as RAG-escalated (avoids double-escalation on config change).
     */
    suspend fun markRagEscalated(messageId: Long): Result<Unit>

    /**
     * Persists the RAG agent's localized warning/guideline for [messageId] and
     * marks it escalated in one write (Flow 1b).
     */
    suspend fun saveRagWarning(
        messageId: Long,
        warning: String,
        guideline: String,
    ): Result<Unit>

    /**
     * Delete messages older than [ttlDays] days. Called by WorkManager cleanup job.
     */
    suspend fun deleteMessagesOlderThan(ttlDays: Long): Result<Int>

    /**
     * Enforce bounded table size: keep only the most recent [maxCount] rows.
     */
    suspend fun enforceMaxMessages(maxCount: Int): Result<Unit>

    /**
     * Data minimization (DPDP Act 2023): replace message bodies older than
     * [bodyTtlDays] with an empty string, keeping only the hash/metadata/
     * classification. Called by the periodic cleanup worker, independently
     * of full-row deletion (§8B).
     */
    suspend fun purgeMessageBodiesOlderThan(bodyTtlDays: Long): Result<Int>
}
