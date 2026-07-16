package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.core.designsystem.theme.warningColor
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkVerdict

/** "Is this the real Bank app?" scanner (§7c 5.1): CameraX + ML Kit Barcode Scanning, plus a manual-paste fallback. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OfficialLinkScannerScreen(
    navController: NavController,
    viewModel: OfficialLinkScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var manualInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) cameraPermissionState.launchPermissionRequest()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Verify Official Bank App", fontWeight = FontWeight.Bold) }) },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (cameraPermissionState.status.isGranted) {
                    QrScannerPreview(onCodeScanned = viewModel::onCodeScanned)
                } else {
                    Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Camera access needed to scan QR codes", color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) { Text("Grant Camera Access") }
                    }
                }

                when (val state = uiState) {
                    is ScannerUiState.Verifying ->
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    is ScannerUiState.Result -> VerdictOverlay(result = state.result, onScanAgain = viewModel::scanAgain)
                    is ScannerUiState.Scanning -> Unit
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Or paste a link / package name", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g. bankname.co.in or com.bank.official") },
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.onCheckManualInput(manualInput) }, enabled = manualInput.isNotBlank()) {
                        Text("Check")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrScannerPreview(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { imageProxy ->
                                @Suppress("DEPRECATION")
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                barcodeScanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull { it.valueType == Barcode.TYPE_URL || it.valueType == Barcode.TYPE_TEXT }
                                            ?.rawValue?.let(onCodeScanned)
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    // No back camera / bind failure — user still has the manual-paste fallback below.
                }
            }, executor)
            previewView
        },
    )
}

@Composable
private fun VerdictOverlay(
    result: com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkCheckResult,
    onScanAgain: () -> Unit,
) {
    val (icon, tint, label) =
        when (result.verdict) {
            OfficialLinkVerdict.VERIFIED_OFFICIAL ->
                Triple(
                    Icons.Filled.CheckCircle,
                    MaterialTheme.colorScheme.safeColor,
                    "VERIFIED OFFICIAL",
                )
            OfficialLinkVerdict.KNOWN_FAKE -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.maliciousColor, "NOT SAFE")
            OfficialLinkVerdict.UNKNOWN -> Triple(Icons.Filled.Help, MaterialTheme.colorScheme.warningColor, "UNKNOWN")
        }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, color = tint, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(result.explanation, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            if (result.verdict == OfficialLinkVerdict.VERIFIED_OFFICIAL) {
                Button(onClick = { /* deep link to Play Store listing */ }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open official YONO Bank on Play Store")
                }
                Spacer(Modifier.height(12.dp))
            }
            TextButton(onClick = onScanAgain) { Text("Scan again", color = Color.White) }
        }
    }
}
