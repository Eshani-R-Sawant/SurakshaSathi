package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.awareness.domain.model.Badge
import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson
import com.sbi.surakshasathi.feature.awareness.domain.model.LessonProgress
import com.sbi.surakshasathi.feature.awareness.domain.model.SafetyNudge
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveBadgesUseCase
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveLessonProgressUseCase
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveLessonsUseCase
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveSafetyNudgesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AwarenessUiState(
    val lessons: List<Lesson> = emptyList(),
    val progress: Map<String, LessonProgress> = emptyMap(),
    val badges: List<Badge> = emptyList(),
    val latestNudge: SafetyNudge? = null,
)

/** Backs the "Learn" tab hub (§7c): scanner entry point, lesson list with progress, badges, latest nudge card. */
@HiltViewModel
class AwarenessViewModel
    @Inject
    constructor(
        observeLessonsUseCase: ObserveLessonsUseCase,
        observeLessonProgressUseCase: ObserveLessonProgressUseCase,
        observeBadgesUseCase: ObserveBadgesUseCase,
        observeSafetyNudgesUseCase: ObserveSafetyNudgesUseCase,
        private val lessonRepository: LessonRepository,
    ) : ViewModel() {
        val uiState =
            combine(
                observeLessonsUseCase("en"),
                observeLessonProgressUseCase(),
                observeBadgesUseCase(),
                observeSafetyNudgesUseCase(),
            ) { lessons, progress, badges, nudges ->
                AwarenessUiState(
                    lessons = lessons,
                    progress = progress.associateBy { it.lessonId },
                    badges = badges,
                    latestNudge = nudges.firstOrNull { !it.seen },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AwarenessUiState())

        init {
            viewModelScope.launch { lessonRepository.refreshLessons("en") }
        }
    }
