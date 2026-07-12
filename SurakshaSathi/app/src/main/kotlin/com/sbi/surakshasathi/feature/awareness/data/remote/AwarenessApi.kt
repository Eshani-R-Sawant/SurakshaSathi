package com.sbi.surakshasathi.feature.awareness.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface AwarenessApi {
    @GET("awareness/lessons")
    suspend fun getLessons(
        @Query("lang") language: String,
    ): List<LessonDto>
}

@Serializable
data class LessonDto(
    val id: String,
    val title: String,
    val description: String,
    val language: String,
    val quiz: List<QuizQuestionDto>,
    val badgeIdOnCompletion: String? = null,
)

@Serializable
data class QuizQuestionDto(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
)
