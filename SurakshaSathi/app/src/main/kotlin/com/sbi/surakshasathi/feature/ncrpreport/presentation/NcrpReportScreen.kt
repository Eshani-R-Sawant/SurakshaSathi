package com.sbi.surakshasathi.feature.ncrpreport.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.theme.safeColor

/** Flow 4b: one-tap NCRP/I4C forensic reporting confirmation screen (§7b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcrpReportScreen(
    navController: NavController,
    viewModel: NcrpReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var consentChecked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report to Cybercrime", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)) {
            when (val state = uiState) {
                is NcrpReportUiState.AwaitingConsent ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Filled.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            "Submit forensic report to I4C / NCRP",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "This will send the offending message/app details, device integrity status, and timestamp to India's national cybercrime portal to help take action and improve detection for everyone. No data is sent without your explicit consent below.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = consentChecked, onCheckedChange = { consentChecked = it })
                            Text("I consent to sharing this report with I4C/NCRP", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = viewModel::onConsentGiven,
                            enabled = consentChecked,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Submit Report")
                        }
                    }
                is NcrpReportUiState.Submitting ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Submitting report…")
                    }
                is NcrpReportUiState.Submitted ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.safeColor,
                            modifier = Modifier.size(72.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Report Submitted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Case ID: ${state.result.caseId}", style = MaterialTheme.typography.titleMedium)
                        if (state.result.isProvisional) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This is a provisional ID — you're currently offline or the portal is unreachable. SurakshaSathi will keep retrying automatically and update this report once confirmed. Nothing is lost.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Done") }
                    }
                is NcrpReportUiState.Error ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::onConsentGiven) { Text("Retry") }
                    }
            }
        }
    }
}
