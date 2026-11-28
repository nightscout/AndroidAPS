package app.aaps.ui.compose.foodManagement

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.openfoodfacts.WithCameraPermission
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

internal actual val foodPhotoCameraAvailable: Boolean = true

@Composable
internal actual fun FoodPhotoCamera(onPhoto: (ByteArray) -> Unit, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        WithCameraPermission(refusedText = UiStrings.food_photo_camera_refused, modifier = modifier) {
            CameraCapture(onPhoto = onPhoto, modifier = modifier)
        }
    }
}

// PROOF OF CONCEPT: the camera set-up is close to `CameraPreview` of the barcode scanner. Before a
// real PR, both should share one camera composable.
@Composable
private fun CameraCapture(onPhoto: (ByteArray) -> Unit, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(LocalContext.current)
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val captureExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            captureExecutor.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            }, mainExecutor)
            previewView
        }
    )
    Button(
        enabled = imageCapture != null && !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val capture = imageCapture ?: return@Button
            busy = true
            capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Made small on the capture thread: a full camera picture is several MB.
                    val bytes = runCatching { image.use { smallJpeg(it) } }.getOrNull()
                    mainExecutor.execute {
                        busy = false
                        bytes?.let { currentOnPhoto(it) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainExecutor.execute { busy = false }
                }
            })
        }
    ) {
        Text(stringResource(UiStrings.food_photo_take))
    }
}

/** The picture turned the right way up, at most [FOOD_PHOTO_MAX_SIZE] pixels on its longest side, as JPEG. */
private fun smallJpeg(image: ImageProxy): ByteArray {
    val full = image.toBitmap()
    val scale = FOOD_PHOTO_MAX_SIZE.toFloat() / max(full.width, full.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        postRotate(image.imageInfo.rotationDegrees.toFloat())
    }
    val small = Bitmap.createBitmap(full, 0, 0, full.width, full.height, matrix, true)
    val out = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    if (small !== full) small.recycle()
    full.recycle()
    return out.toByteArray()
}

private const val JPEG_QUALITY = 85
