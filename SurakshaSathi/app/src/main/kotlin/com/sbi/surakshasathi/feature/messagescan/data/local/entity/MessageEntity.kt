package com.sbi.surakshasathi.feature.messagescan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource

/**
 * Room entity for persisting classified messages.
 *
 * Storage design (§8B data minimization):
 * - [body] is the raw text; purged after [MESSAGE_TTL_DAYS] by WorkManager cleanup.
 * - [bodyHash] (SHA-256) is retained permanently for deduplication and analytics.
 * - [extractedUrls] stored as a pipe-separated string (no nested entities needed).
 *
 * Index on [receivedAtMillis] for efficient time-range queries.
 * Index on [classification] for fast flagged-message queries.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["received_at_millis"]),
        Index(value = ["classification"]),
        Index(value = ["body_hash"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "body_hash")
    val bodyHash: String,
    @ColumnInfo(name = "sender")
    val sender: String,
    @ColumnInfo(name = "source")
    val source: String, // MessageSource.name()
    @ColumnInfo(name = "received_at_millis")
    val receivedAtMillis: Long,
    /** Pipe-separated URL list: "https://a.com|https://b.com" */
    @ColumnInfo(name = "extracted_urls")
    val extractedUrlsRaw: String = "",
    @ColumnInfo(name = "classification")
    val classification: String, // MessageClassification.name()
    @ColumnInfo(name = "risk_score")
    val riskScore: Float = 0f,
    @ColumnInfo(name = "ml_score")
    val mlScore: Float = 0f,
    @ColumnInfo(name = "rule_score")
    val ruleScore: Float = 0f,
    @ColumnInfo(name = "rag_escalated")
    val ragEscalated: Boolean = false,
    @ColumnInfo(name = "rag_warning_text")
    val ragWarningText: String? = null,
    @ColumnInfo(name = "rag_guideline_text")
    val ragGuidelineText: String? = null,
) {
    fun toDomain(): Message =
        Message(
            id = id,
            body = body,
            bodyHash = bodyHash,
            sender = sender,
            source = runCatching { MessageSource.valueOf(source) }.getOrDefault(MessageSource.UNKNOWN),
            receivedAtMillis = receivedAtMillis,
            extractedUrls =
                if (extractedUrlsRaw.isBlank()) {
                    emptyList()
                } else {
                    extractedUrlsRaw.split("|").filter { it.isNotBlank() }
                },
            classification =
                runCatching { MessageClassification.valueOf(classification) }
                    .getOrDefault(MessageClassification.UNCLASSIFIED),
            riskScore = riskScore,
            mlScore = mlScore,
            ruleScore = ruleScore,
            ragEscalated = ragEscalated,
            ragWarningText = ragWarningText,
            ragGuidelineText = ragGuidelineText,
        )

    companion object {
        fun fromDomain(m: Message): MessageEntity =
            MessageEntity(
                id = m.id,
                body = m.body,
                bodyHash = m.bodyHash,
                sender = m.sender,
                source = m.source.name,
                receivedAtMillis = m.receivedAtMillis,
                extractedUrlsRaw = m.extractedUrls.joinToString("|"),
                classification = m.classification.name,
                riskScore = m.riskScore,
                mlScore = m.mlScore,
                ruleScore = m.ruleScore,
                ragEscalated = m.ragEscalated,
                ragWarningText = m.ragWarningText,
                ragGuidelineText = m.ragGuidelineText,
            )
    }
}
