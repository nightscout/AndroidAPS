package app.aaps.pump.ap550s.compose.qr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.pump.ap550s.R
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors
import kotlin.math.min

/** Side of the scan square, as a part of the shorter screen side. */
private const val VIEWFINDER_RATIO = 0.7f

/** Length of a corner bracket, as a part of the scan square side. */
private const val CORNER_RATIO = 0.18f

/** How far the laser line travels in one pass. */
private const val LASER_PERIOD_MS = 2_500

/** Blocking scan area is drawn over the camera image. */
private const val MASK_ALPHA = 0.6f

/** Thickness of the scan square frame and of the laser line. */
private val FRAME_STROKE = 3.dp

/** Padding of the hint text that sits under the scan square. */
private val HINT_PADDING_HORIZONTAL = 32.dp
private val HINT_PADDING_BOTTOM = 48.dp

/** How visible the laser line is over the camera image. */
private const val LASER_ALPHA = 0.8f

/**
 * Reads the QR code on the back of the pump.
 *
 * The camera image stays visible behind a scan square. A barcode is fed to [Ap550QrParser]; anything
 * that is not a pump code is reported under the square and scanning carries on, so the user only has
 * to move the phone. [onScanned] is called once a pump code was read and parsed.
 */
@Composable
fun Ap550QrScanScreen(
    onScanned: (serialNumber: String, deviceName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // The analyzer runs on a background thread, so the parse result travels back through a flow that
    // the composable collects on the main thread.
    val scanResults = remember { MutableSharedFlow<Ap550QrParseResult>(extraBufferCapacity = 5) }
    val unrecognizedText = stringResource(R.string.ap550_qr_unrecognized)
    val unknownDeviceText = stringResource(R.string.ap550_unknown_device_type)

    LaunchedEffect(Unit) {
        scanResults.collectLatest { result ->
            when (result) {
                is Ap550QrParseResult.Success -> onScanned(result.serialNumber, result.deviceName)
                Ap550QrParseResult.Unrecognized -> errorText = unrecognizedText
                Ap550QrParseResult.UnknownDeviceType -> errorText = unknownDeviceText
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            CameraPreview(
                onCodeScanned = { scanResults.tryEmit(it) },
                modifier = Modifier.fillMaxSize()
            )
            ViewfinderOverlay(modifier = Modifier.fillMaxSize())
        }
        // Which code to look for. White on purpose: the background is always black here, whatever
        // the theme says.
        Text(
            text = stringResource(R.string.ap550_ble_config_qr_scan_summary),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = HINT_PADDING_BOTTOM)
        )
        Text(
            text = errorText ?: stringResource(if (cameraGranted) R.string.ap550_qr_scan_hint else R.string.ap550_qr_camera_permission),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = HINT_PADDING_HORIZONTAL, vertical = HINT_PADDING_BOTTOM)
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Resolution the camera is asked to give the analyzer.
 *
 * The CameraX default is much lower, and a small frame makes the QR code too small to decode, so the
 * scan finds nothing while the preview looks fine. The old driver asked for about 720p, 16:9, and
 * the frame is requested in landscape, which is how CameraX reports its stream sizes.
 */
private fun analysisResolutionSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
        )
        .build()

/**
 * Camera preview with a barcode analyzer on top.
 *
 * Everything is unbound and closed when the screen leaves the composition, so the camera is not held
 * while the pairing wizard shows another step.
 */
@Composable
private fun CameraPreview(
    onCodeScanned: (Ap550QrParseResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolutionSelector())
                    // The analyzer copies the frame to NV21, which needs this exact YUV layout.
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(analyzerExecutor) { imageProxy ->
                            analyzeImage(imageProxy, barcodeScanner, onCodeScanned)
                        }
                    }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            cameraProvider?.unbindAll()
            barcodeScanner.close()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Hands one camera frame to ML Kit and reports the first barcode it holds.
 *
 * The frame is copied to NV21 first and ML Kit is given that copy, not the frame itself. The YUV
 * planes of a CameraX frame can carry padding, and ML Kit reads a padded plane as a broken picture,
 * so the code is never found. The old driver made the same copy for the same reason. The frame is
 * closed once the recognition finished, which is what lets the next frame through.
 */
private fun analyzeImage(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    onCodeScanned: (Ap550QrParseResult) -> Unit
) {
    // A fresh copy per frame: ML Kit reads the array after this call returns, so the same array must
    // not be written again while a recognition is still running.
    val imageSize = imageProxy.width * imageProxy.height
    val nv21 = ByteArray(imageSize + 2 * (imageSize / 4))
    imageProxy.copyToNv21(nv21)
    val image = InputImage.fromByteArray(
        nv21,
        imageProxy.width,
        imageProxy.height,
        imageProxy.imageInfo.rotationDegrees,
        InputImage.IMAGE_FORMAT_NV21
    )
    barcodeScanner.process(image)
        .addOnSuccessListener { barcodes ->
            val contents = barcodes.firstOrNull()?.rawValue
            if (contents != null) onCodeScanned(Ap550QrParser.parse(contents))
        }
        .addOnCompleteListener { imageProxy.close() }
}

/**
 * Copies one YUV_420_888 camera frame into an NV21 byte array.
 *
 * A plane can have a row stride larger than the frame width, so each row is copied on its own and
 * the padding behind it is skipped. The chroma planes are interleaved the way NV21 wants them (V
 * first, then U), also honouring their strides.
 */
private fun ImageProxy.copyToNv21(nv21: ByteArray) {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer.apply { rewind() }
    val uBuffer = uPlane.buffer.apply { rewind() }
    val vBuffer = vPlane.buffer.apply { rewind() }
    val ySize = yBuffer.remaining()

    var position = 0
    repeat(height) {
        yBuffer.get(nv21, position, width)
        position += width
        yBuffer.position(minOf(ySize, yBuffer.position() - width + yPlane.rowStride))
    }

    val chromaHeight = height / 2
    val chromaWidth = width / 2
    val vRowStride = vPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val uPixelStride = uPlane.pixelStride
    val vLine = ByteArray(vRowStride)
    val uLine = ByteArray(uRowStride)

    repeat(chromaHeight) {
        vBuffer.get(vLine, 0, minOf(vRowStride, vBuffer.remaining()))
        uBuffer.get(uLine, 0, minOf(uRowStride, uBuffer.remaining()))
        var vIndex = 0
        var uIndex = 0
        repeat(chromaWidth) {
            nv21[position++] = vLine[vIndex]
            nv21[position++] = uLine[uIndex]
            vIndex += vPixelStride
            uIndex += uPixelStride
        }
    }
}

/** Darkens everything except the scan square, then draws its frame and a moving laser line. */
@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val maskColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASK_ALPHA)
    val frameColor = MaterialTheme.colorScheme.primary
    val laser by rememberInfiniteTransition(label = "laser").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LASER_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "laser"
    )

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height) * VIEWFINDER_RATIO
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side

        drawRect(maskColor, size = Size(size.width, top))
        drawRect(maskColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(maskColor, topLeft = Offset(0f, top), size = Size(left, side))
        drawRect(maskColor, topLeft = Offset(right, top), size = Size(size.width - right, side))

        val stroke = FRAME_STROKE.toPx()
        val corner = side * CORNER_RATIO
        // Top left and top right.
        drawLine(frameColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
        drawLine(frameColor, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
        drawLine(frameColor, Offset(right, top), Offset(right - corner, top), stroke, StrokeCap.Round)
        drawLine(frameColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
        // Bottom left and bottom right.
        drawLine(frameColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
        drawLine(frameColor, Offset(left, bottom), Offset(left, bottom - corner), stroke, StrokeCap.Round)
        drawLine(frameColor, Offset(right, bottom), Offset(right - corner, bottom), stroke, StrokeCap.Round)
        drawLine(frameColor, Offset(right, bottom), Offset(right, bottom - corner), stroke, StrokeCap.Round)

        val laserY = top + side * laser
        drawLine(frameColor.copy(alpha = LASER_ALPHA), Offset(left, laserY), Offset(right, laserY), stroke)
    }
}