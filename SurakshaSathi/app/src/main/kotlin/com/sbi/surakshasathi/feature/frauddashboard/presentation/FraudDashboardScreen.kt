package com.sbi.surakshasathi.feature.frauddashboard.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.FraudCluster
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.UserSegment

/**
 * Flow 4a: fraud heatmap + one-tap segment alert push (§7).
 *
 * Renders a real Google Maps heatmap when `MAPS_API_KEY` is configured;
 * otherwise falls back to a Compose Canvas-drawn approximation over India's
 * lat/lng bounding box so the dashboard NEVER fails to render for a demo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FraudDashboardScreen(
    navController: NavController,
    viewModel: FraudDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pushState by viewModel.pushAlertState.collectAsStateWithLifecycle()
    var showPushSheet by remember { mutableStateOf<UserSegment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fraud Heatmap", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is FraudDashboardUiState.Loading ->
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                is FraudDashboardUiState.Success -> {
                    if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
                        GoogleMapsHeatmap(clusters = state.clusters)
                    } else {
                        CanvasHeatmapFallback(clusters = state.clusters)
                    }

                    Text(
                        "Active Campaigns",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.clusters.distinctBy { it.region to it.persona }, key = { it.region + it.persona }) { cluster ->
                            CampaignCard(
                                cluster = cluster,
                                onPush = { showPushSheet = UserSegment(cluster.region, cluster.persona, "en") },
                            )
                        }
                    }
                }
            }
        }
    }

    val segment = showPushSheet
    if (segment != null) {
        PushAlertDialog(
            segment = segment,
            pushState = pushState,
            onDismiss = {
                showPushSheet = null
                viewModel.dismissPushState()
            },
            onSend = { message -> viewModel.pushAlert(segment, message) },
        )
    }
}

@Composable
private fun CanvasHeatmapFallback(clusters: List<FraudCluster>) {
    // India's rough bounding box for a simple, always-renderable lat/lng → canvas projection.
    val minLat = 6.0
    val maxLat = 37.0
    val minLng = 68.0
    val maxLng = 97.0

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D1B2A)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                clusters.forEach { cluster ->
                    val x = ((cluster.lng - minLng) / (maxLng - minLng)).coerceIn(0.0, 1.0) * size.width
                    val y = (1.0 - (cluster.lat - minLat) / (maxLat - minLat)).coerceIn(0.0, 1.0) * size.height
                    val radius = 24f + cluster.weight * 40f
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFF5252).copy(alpha = 0.55f * cluster.weight + 0.15f),
                                        Color.Transparent,
                                    ),
                                center = Offset(x.toFloat(), y.toFloat()),
                                radius = radius,
                            ),
                        radius = radius,
                        center = Offset(x.toFloat(), y.toFloat()),
                    )
                }
            }
            Text(
                "Approximate visualization — no Maps API key configured",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun GoogleMapsHeatmap(clusters: List<FraudCluster>) {
    // Real Google Maps Compose path — active only when MAPS_API_KEY is configured (§7).
    // Weighted circle overlays approximate a heatmap using core Maps Compose primitives
    // (no extra heatmap-utils dependency needed).
    val cameraPositionState =
        com.google.maps.android.compose.rememberCameraPositionState {
            position =
                com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(22.0, 79.0), 4.2f,
                )
        }
    com.google.maps.android.compose.GoogleMap(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        cameraPositionState = cameraPositionState,
    ) {
        clusters.forEach { cluster ->
            com.google.maps.android.compose.Circle(
                center = com.google.android.gms.maps.model.LatLng(cluster.lat, cluster.lng),
                radius = 20_000.0 + cluster.weight * 60_000.0,
                fillColor = Color(0xFFFF5252).copy(alpha = 0.35f * cluster.weight + 0.1f),
                strokeColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun CampaignCard(
    cluster: FraudCluster,
    onPush: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${cluster.region} — ${cluster.persona.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    cluster.campaignTag.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onPush) {
                Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Push alert")
            }
        }
    }
}

@Composable
private fun PushAlertDialog(
    segment: UserSegment,
    pushState: PushAlertState,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Push vernacular alert") },
        text = {
            Column {
                Text(
                    "Segment: ${segment.persona} · ${segment.region} (topic: ${segment.toFcmTopic()})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Alert message") },
                    modifier = Modifier.fillMaxWidth(),
                )
                when (pushState) {
                    is PushAlertState.Sending -> Text("Sending…", modifier = Modifier.padding(top = 8.dp))
                    is PushAlertState.Sent ->
                        Text(
                            "Sent!",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    is PushAlertState.Failed ->
                        Text(
                            pushState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    is PushAlertState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(message) }, enabled = message.isNotBlank() && pushState !is PushAlertState.Sending) {
                Text("Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
