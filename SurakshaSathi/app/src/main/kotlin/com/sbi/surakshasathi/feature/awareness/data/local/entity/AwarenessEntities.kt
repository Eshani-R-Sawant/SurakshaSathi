package com.sbi.surakshasathi.feature.awareness.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Quiz stored as JSON — simpler than a join table for a handful of questions per lesson. */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val language: String,
    @ColumnInfo(name = "quiz_json") val quizJson: String,
    @ColumnInfo(name = "badge_id_on_completion") val badgeIdOnCompletion: String?,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey @ColumnInfo(name = "lesson_id") val lessonId: String,
    val completed: Boolean,
    @ColumnInfo(name = "correct_answers") val correctAnswers: Int,
    @ColumnInfo(name = "total_questions") val totalQuestions: Int,
    @ColumnInfo(name = "last_attempted_at_millis") val lastAttemptedAtMillis: Long,
)

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "earned_at_millis") val earnedAtMillis: Long?,
)

@Entity(tableName = "safety_nudges")
data class SafetyNudgeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "video_url") val videoUrl: String?,
    val language: String,
    val persona: String,
    val region: String,
    val priority: Int,
    val seen: Boolean,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)
