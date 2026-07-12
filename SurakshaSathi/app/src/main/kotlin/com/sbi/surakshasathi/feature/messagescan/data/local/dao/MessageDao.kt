package com.sbi.surakshasathi.feature.messagescan.data.local.dao

import androidx.room.*
import com.sbi.surakshasathi.feature.messagescan.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the messages table.
 *
 * All queries return [Flow] for reactive observation or suspend for one-shots.
 * Bounded retention is enforced by [deleteOlderThan] and [deleteOldestBeyondLimit],
 * called periodically by WorkManager (§8B).
 */
@Dao
interface MessageDao {
    /** Reactive: emits full list whenever table changes. */
    @Query("SELECT * FROM messages ORDER BY received_at_millis DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    /** Reactive: only SUSPICIOUS or MALICIOUS messages. */
    @Query(
        """
        SELECT * FROM messages
        WHERE classification IN ('SUSPICIOUS', 'MALICIOUS')
        ORDER BY received_at_millis DESC
    """,
    )
    fun observeFlagged(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageEntity?

    /** Returns the existing row if [bodyHash] already exists (dedup). */
    @Query("SELECT * FROM messages WHERE body_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageEntity): Long

    @Update
    suspend fun update(entity: MessageEntity)

    /** Update only the rag_escalated flag — avoids full row write. */
    @Query("UPDATE messages SET rag_escalated = 1 WHERE id = :id")
    suspend fun markRagEscalated(id: Long)

    /** Persists the RAG agent's localized warning/guideline text for a message. */
    @Query(
        """
        UPDATE messages
        SET rag_escalated = 1, rag_warning_text = :warning, rag_guideline_text = :guideline
        WHERE id = :id
    """,
    )
    suspend fun saveRagWarning(
        id: Long,
        warning: String,
        guideline: String,
    )

    // ── Retention / bounded table (§8B) ──────────────────────────────────────

    /** Delete messages older than a given epoch millis threshold. Returns deleted count. */
    @Query("DELETE FROM messages WHERE received_at_millis < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long): Int

    /** Count of all messages. */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    /**
     * Delete the oldest messages so the table never exceeds [maxCount] rows.
     * Uses a subquery to find the cutoff ID, then deletes below it.
     */
    @Query(
        """
        DELETE FROM messages WHERE id IN (
            SELECT id FROM messages
            ORDER BY received_at_millis ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM messages) - :maxCount)
        )
    """,
    )
    suspend fun deleteOldestBeyondLimit(maxCount: Int): Int

    /** Purge message bodies (replace with empty string) after TTL — keep hash + metadata. */
    @Query(
        """
        UPDATE messages
        SET body = ''
        WHERE received_at_millis < :thresholdMillis AND body != ''
    """,
    )
    suspend fun purgeBodyOlderThan(thresholdMillis: Long): Int
}
