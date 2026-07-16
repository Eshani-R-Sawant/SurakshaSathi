package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.awareness.data.repository.BundledFakeAppRounds
import com.sbi.surakshasathi.feature.awareness.domain.model.FakeAppRound
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ScenarioRecapItem
import com.sbi.surakshasathi.feature.awareness.domain.model.buildGameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.isBadgeEarned
import com.sbi.surakshasathi.feature.awareness.domain.usecase.PersonaScenarioSelector
import com.sbi.surakshasathi.feature.awareness.domain.usecase.RecordGameOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROUND_SIZE = 5

sealed interface FakeAppDetectiveUiState {
    data object Loading : FakeAppDetectiveUiState

    data class InProgress(
        val rounds: List<FakeAppRound>,
        val roundIndex: Int,
        val recap: List<ScenarioRecapItem>,
    ) : FakeAppDetectiveUiState

    data class Finished(val outcome: GameOutcome, val badgeEarned: Boolean) : FakeAppDetectiveUiState
}

/** Tap the genuine app tile among lookalikes (§7c Phase 7). */
@HiltViewModel
class FakeAppDetectiveViewModel
    @Inject
    constructor(
        private val recordGameOutcomeUseCase: RecordGameOutcomeUseCase,
        private val userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FakeAppDetectiveUiState>(FakeAppDetectiveUiState.Loading)
        val uiState: StateFlow<FakeAppDetectiveUiState> = _uiState.asStateFlow()

        init {
            startNewRound()
        }

        fun onTileSelected(tileId: String) {
            val state = _uiState.value as? FakeAppDetectiveUiState.InProgress ?: return
            val round = state.rounds[state.roundIndex]
            val chosen = round.tiles.first { it.id == tileId }
            val recapItem = ScenarioRecapItem(round.id, round.prompt, chosen.isGenuine, round.explanation)
            val nextRecap = state.recap + recapItem
            val nextIndex = state.roundIndex + 1

            if (nextIndex >= state.rounds.size) {
                val outcome = buildGameOutcome(GameType.FAKE_APP_DETECTIVE.id, nextRecap)
                viewModelScope.launch { recordGameOutcomeUseCase(outcome) }
                _uiState.value = FakeAppDetectiveUiState.Finished(outcome, outcome.isBadgeEarned())
            } else {
                _uiState.value = state.copy(roundIndex = nextIndex, recap = nextRecap)
            }
        }

        fun onPlayAgain() {
            startNewRound()
        }

        private fun startNewRound() {
            _uiState.value = FakeAppDetectiveUiState.Loading
            viewModelScope.launch {
                val persona = userPreferencesDataStore.userPreferences.first().userPersona
                val rounds =
                    PersonaScenarioSelector.pickRound(
                        pool = BundledFakeAppRounds.ENGLISH,
                        personaTagsOf = { it.personaTags },
                        persona = persona,
                        roundSize = ROUND_SIZE,
                    )
                _uiState.value = FakeAppDetectiveUiState.InProgress(rounds, roundIndex = 0, recap = emptyList())
            }
        }
    }
