package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.feature.awareness.domain.model.AppTile
import com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType

/** "Fake App Detective": tap the genuine app among lookalikes (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeAppDetectiveScreen(
    navController: NavController,
    viewModel: FakeAppDetectiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reward = GameBadges.BY_GAME[GameType.FAKE_APP_DETECTIVE.id]
    val theme = GameType.FAKE_APP_DETECTIVE.theme()

    Scaffold(topBar = { TopAppBar(title = { Text("Fake App Detective") }) }) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues)
                    .background(GameType.FAKE_APP_DETECTIVE.screenBackgroundBrush()),
        ) {
            when (val state = uiState) {
                is FakeAppDetectiveUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is FakeAppDetectiveUiState.InProgress -> {
                    val round = state.rounds[state.roundIndex]
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        LinearProgressIndicator(
                            progress = { state.roundIndex.toFloat() / state.rounds.size },
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Round ${state.roundIndex + 1} of ${state.rounds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(round.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(round.tiles, key = { _, tile -> tile.id }) { index, tile ->
                                AppTileCard(tile = tile, index = index, onClick = { viewModel.onTileSelected(tile.id) })
                            }
                        }
                    }
                }
                is FakeAppDetectiveUiState.Finished ->
                    GameResultCard(
                        outcome = state.outcome,
                        badgeEarned = state.badgeEarned,
                        badgeTitle = reward?.title,
                        gameTheme = theme,
                        onBrowseAdvisories = { navController.navigate(Screen.AdvisoryList.route) },
                        onPlayAgain = viewModel::onPlayAgain,
                        onBackToHub = { navController.popBackStack() },
                    )
            }
        }
    }
}

@Composable
private fun AppTileCard(
    tile: AppTile,
    index: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(BUBBLE_PALETTE[index.mod(BUBBLE_PALETTE.size)]),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tile.label.first().uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.label, fontWeight = FontWeight.SemiBold)
                Text(tile.developer, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(
                    "${tile.downloads} · ${tile.rating}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
