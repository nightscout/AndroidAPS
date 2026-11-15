package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
internal actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        refused = !allowed
    }
    // Asked here and not in the setup wizard's permission list: only a client that scans needs the camera.
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) CameraPreview(onScanned = onScanned, modifier = modifier)
    else Box(modifier = modifier.padding(AapsSpacing.xxLarge), contentAlignment = Alignment.Center) {
        if (refused) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            Text(
                text = stringResource(SyncStrings.client_setup_scan_camera_refused),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(SyncStrings.pair_with_master_retry))
            }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    // Kept so the camera can be let go when the picture leaves the screen, not only when the screen closes.
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val mainExecutor = ContextCompat.getMainExecutor(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, QrCodeAnalyzer { text -> mainExecutor.execute { currentOnScanned(text) } }) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, mainExecutor)
            previewView
        }
    )
}

/** Looks for a QR code in the brightness plane of each camera frame. Runs on the analysis thread. */
private class QrCodeAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            val text = try {
                reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
            } catch (_: NotFoundException) {
                null
            } finally {
                reader.reset()
            }
            if (!text.isNullOrEmpty()) onDecoded(text)
        } catch (_: Exception) {
            // A frame that cannot be read (a damaged code, a checksum error): the next frame is tried.
        } finally {
            image.close()
        }
    }
}
