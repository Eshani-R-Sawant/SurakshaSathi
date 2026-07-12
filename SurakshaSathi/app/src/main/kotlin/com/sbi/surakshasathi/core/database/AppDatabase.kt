package com.sbi.surakshasathi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.PhishingDomainDao
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.ThreatHashDao
import com.sbi.surakshasathi.feature.apkscan.data.local.entity.PhishingDomainEntity
import com.sbi.surakshasathi.feature.apkscan.data.local.entity.ThreatHashEntity
import com.sbi.surakshasathi.feature.awareness.data.local.dao.BadgeDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.LessonDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.LessonProgressDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.SafetyNudgeDao
import com.sbi.surakshasathi.feature.awareness.data.local.entity.BadgeEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.LessonEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.LessonProgressEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.SafetyNudgeEntity
import com.sbi.surakshasathi.feature.messagescan.data.local.dao.MessageDao
import com.sbi.surakshasathi.feature.messagescan.data.local.entity.MessageEntity
import com.sbi.surakshasathi.feature.ncrpreport.data.local.dao.NcrpReportDao
import com.sbi.surakshasathi.feature.ncrpreport.data.local.entity.NcrpReportEntity

/**
 * Single encrypted Room database for SurakshaSathi.
 *
 * Encrypted via SQLCipher with a Keystore-derived passphrase.
 * See [com.sbi.surakshasathi.core.di.DatabaseModule] for the setup.
 *
 * Version history:
 *   v1 — Phase 1: messages table
 *   v2 — Phase 2: messages.rag_warning_text / rag_guideline_text (Flow 1b)
 *   v3 — Phase 3: threat_hashes, phishing_domains tables
 *   v4 — Phase 5: ncrp_reports table (Flow 4b)
 *   v5 — Phase 6: lessons, lesson_progress, badges, safety_nudges (Flow 5)
 *
 * Pre-release: schema bumps use [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
 * (see DatabaseModule) since there is no shipped user data yet. Replace with
 * real [androidx.room.migration.Migration]s before any production release.
 * Retention enforced by WorkManager cleanup job (§8B).
 */
@Database(
    entities = [
        MessageEntity::class,
        ThreatHashEntity::class,
        PhishingDomainEntity::class,
        NcrpReportEntity::class,
        LessonEntity::class,
        LessonProgressEntity::class,
        BadgeEntity::class,
        SafetyNudgeEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun threatHashDao(): ThreatHashDao

    abstract fun phishingDomainDao(): PhishingDomainDao

    abstract fun ncrpReportDao(): NcrpReportDao

    abstract fun lessonDao(): LessonDao

    abstract fun lessonProgressDao(): LessonProgressDao

    abstract fun badgeDao(): BadgeDao

    abstract fun safetyNudgeDao(): SafetyNudgeDao

    companion object {
        const val DB_NAME = "surakshasathi.db"

        // Retention constants (§8B)
        const val MAX_MESSAGES = 1_000
        const val MAX_THREAT_HASHES = 10_000 // LRU hot cache
        const val MESSAGE_TTL_DAYS = 30L
        const val SCAN_RESULT_TTL_DAYS = 90L
        const val MAX_SAFETY_NUDGES = 100 // Caps cached vernacular video URLs too
    }
}
