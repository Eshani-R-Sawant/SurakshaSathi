package com.sbi.surakshasathi.core.di

import android.content.Context
import androidx.room.Room
import com.sbi.surakshasathi.core.database.AppDatabase
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.PhishingDomainDao
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.ThreatHashDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.BadgeDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.LessonDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.LessonProgressDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.SafetyNudgeDao
import com.sbi.surakshasathi.feature.messagescan.data.local.dao.MessageDao
import com.sbi.surakshasathi.feature.ncrpreport.data.local.dao.NcrpReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * Provides the encrypted Room database and all DAOs.
 *
 * Security: Room is encrypted with SQLCipher. The passphrase is derived from
 * the Android Keystore — never stored in plain text or source code.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DB_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    // ── Phase 1 DAOs ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    // ── Phase 3 DAOs ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideThreatHashDao(db: AppDatabase): ThreatHashDao = db.threatHashDao()

    @Provides
    @Singleton
    fun providePhishingDomainDao(db: AppDatabase): PhishingDomainDao = db.phishingDomainDao()

    // ── Phase 5 DAOs ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideNcrpReportDao(db: AppDatabase): NcrpReportDao = db.ncrpReportDao()

    // ── Phase 6 DAOs ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideLessonDao(db: AppDatabase): LessonDao = db.lessonDao()

    @Provides
    @Singleton
    fun provideLessonProgressDao(db: AppDatabase): LessonProgressDao = db.lessonProgressDao()

    @Provides
    @Singleton
    fun provideBadgeDao(db: AppDatabase): BadgeDao = db.badgeDao()

    @Provides
    @Singleton
    fun provideSafetyNudgeDao(db: AppDatabase): SafetyNudgeDao = db.safetyNudgeDao()
}
