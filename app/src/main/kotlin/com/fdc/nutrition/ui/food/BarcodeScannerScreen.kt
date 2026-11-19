package com.fdc.nutrition.ui.food

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.fdc.nutrition.data.repository.BarcodeOutcome
import com.fdc.nutrition.data.repository.ProductImportRepository
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.Executors
import javax.inject.Inject

sealed interface ScanUiState {
    data object Scanning : ScanUiState
    data class Looking(val code: String) : ScanUiState
    data class NotFound(val code: String) : ScanUiState
    data class Unavailable(val code: String, val reason: String) : ScanUiState
}

@HiltViewModel
class BarcodeViewModel @Inject constructor(savedState: SavedStateHandle, private val imports: ProductImportRepository, private val drafts: PendingDraftStore) : BaseViewModel() {
    val route = savedState.toRoute<ScannerRoute>()
    var state by mutableStateOf<ScanUiState>(ScanUiState.Scanning)

    fun lookup(code: String, onLocal: (String) -> Unit, onRemote: (String) -> Unit) {
        if (state is ScanUiState.Looking) return
        val c = code.trim()
        if (!c.matches(Regex("\\d{6,14}"))) { message("Barcodes are 6–14 digits"); return }
        state = ScanUiState.Looking(c)
        launchSafe {
            state = when (val r = imports.lookupBarcode(c)) {
                is BarcodeOutcome.Local -> { onLocal(r.foodId); ScanUiState.Scanning }
                is BarcodeOutcome.Remote -> { onRemote(drafts.put(r.result.draft, listOfNotNull("Found on Open Food Facts. Check the values against the package before saving.", r.result.completenessNote).joinToString(" "))); ScanUiState.Scanning }
                BarcodeOutcome.NotFound -> ScanUiState.NotFound(c)
                is BarcodeOutcome.Unavailable -> ScanUiState.Unavailable(c, r.reason)
            }
        }
    }
    fun reset() { state = ScanUiState.Scanning }
}

@Composable
fun BarcodeScannerScreen(nav: NavHostController, vm: BarcodeViewModel = hiltViewModel()) {
    CollectMessages(vm.messages)
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var manual by remember { mutableStateOf("") }
    val r = vm.route
    val onLocal: (String) -> Unit = { id -> nav.navigate(FoodDetailRoute(id, r.date, r.mealId, if (r.date != null) MODE_LOG else MODE_BROWSE)) { popUpTo<ScannerRoute> { inclusive = true } } }
    val onRemote: (String) -> Unit = { key -> nav.navigate(FoodEditorRoute(draftKey = key, date = r.date, mealId = r.mealId)) { popUpTo<ScannerRoute> { inclusive = true } } }

    Scaffold(topBar = { BackTopBar("Scan barcode", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (granted) {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    CameraBarcodePreview { code -> if (vm.state is ScanUiState.Scanning) vm.lookup(code, onLocal, onRemote) }
                }
            } else {
                AppCard {
                    Text("Camera access is needed to scan barcodes.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Allow camera") }
                    Text("You can also type the barcode below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (val s = vm.state) {
                is ScanUiState.Looking -> Row { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Looking up ${s.code}…") }
                is ScanUiState.NotFound -> {
                    Banner("Product ${s.code} wasn't found in Open Food Facts. Nothing was guessed — enter it from the label.", BannerKind.WARNING)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nav.navigate(FoodEditorRoute(barcode = s.code, date = r.date, mealId = r.mealId)) }, modifier = Modifier.testTag("enter_manually")) { Text("Enter manually") }
                        OutlinedButton(onClick = vm::reset) { Text("Scan again") }
                    }
                }
                is ScanUiState.Unavailable -> {
                    Banner(s.reason, BannerKind.OFFLINE)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nav.navigate(FoodEditorRoute(barcode = s.code, date = r.date, mealId = r.mealId)) }) { Text("Enter manually") }
                        OutlinedButton(onClick = { vm.reset(); vm.lookup(s.code, onLocal, onRemote) }) { Text("Retry") }
                    }
                }
                ScanUiState.Scanning -> Text("Point the camera at a product barcode.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(manual, { v -> manual = v.filter { it.isDigit() }.take(14) }, label = { Text("Or type the barcode") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("manual_barcode"))
            Button(onClick = { vm.reset(); vm.lookup(manual, onLocal, onRemote) }, enabled = manual.length >= 6, modifier = Modifier.fillMaxWidth().testTag("lookup_barcode")) { Text("Look up") }
            Text("Product data from Open Food Facts (ODbL). Scanned products are saved on your device for offline use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** CameraX preview with ML Kit barcode analysis (EAN/UPC formats). */
@Composable
private fun CameraBarcodePreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build())
    }
    val latest by rememberUpdatedState(onCode)
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }
    AndroidView(factory = { ctx ->
        val view = PreviewView(ctx)
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes -> codes.firstOrNull { it.rawValue != null }?.rawValue?.let { v -> ContextCompat.getMainExecutor(ctx).execute { latest(v) } } }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching { provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
        }, ContextCompat.getMainExecutor(ctx))
        view
    }, modifier = Modifier.fillMaxSize())
}
