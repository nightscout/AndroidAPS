@file:OptIn(ExperimentalForeignApi::class)

package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/** What the camera can do for us right now. Kept apart from the UI so [cameraState] can be tested. */
internal enum class CameraState { Asking, Ready, Denied, Blocked, Unavailable }

/**
 * Turns the two things iOS tells us into one state.
 *
 * Pure on purpose: everything else in this file needs a real camera, this does not.
 *
 * [status] is an `AVAuthorizationStatus`, which Kotlin/Native sees as a plain [Long] and not as an
 * enum, so the `when` needs an `else`. An unknown future status is treated as "ask": a refusal then
 * lands on [CameraState.Denied] and corrects itself.
 */
internal fun cameraState(hasDevice: Boolean, status: Long): CameraState =
    if (!hasDevice) CameraState.Unavailable
    else when (status) {
        AVAuthorizationStatusAuthorized    -> CameraState.Ready
        AVAuthorizationStatusDenied        -> CameraState.Denied
        AVAuthorizationStatusRestricted    -> CameraState.Blocked
        AVAuthorizationStatusNotDetermined -> CameraState.Asking
        else                               -> CameraState.Asking
    }

/**
 * Hands every QR code it sees to [onText].
 *
 * Held strongly by [QrCapture] for as long as the picture is on screen. `metadataObjectsDelegate` is
 * declared `readonly, nullable` with no `weak`, so ARC should retain it - but this repo has already
 * been caught by the opposite: `IosNotificationDelegate` records that a locally created delegate is
 * collected and the callbacks simply stop, which looks exactly like iOS never calling back.
 */
private class QrMetadataDelegate(
    private val onText: State<(String) -> Unit>
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        for (found in didOutputMetadataObjects) {
            // stringValue is null when the payload makes no string. Skip it, the way the Android
            // side simply swallows a frame it cannot decode.
            val text = (found as? AVMetadataMachineReadableCodeObject)?.stringValue
            if (!text.isNullOrEmpty()) onText.value(text)
        }
    }
}

/**
 * The view the preview layer is drawn into.
 *
 * The layer needs its frame set again whenever the view is laid out; `update` on [UIKitView] is not
 * the place for that, because it runs on state change and not on layout.
 */
private class CameraPreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer
) : UIView(frame = CGRectZero.readValue()) {

    init {
        clipsToBounds = true
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Without this the first layout animates the resize, which looks like the picture sliding in.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }
}

/**
 * The camera session, its delegate, its queue and its preview layer, as one thing with one
 * [dispose].
 *
 * Built by [open], which answers null when this device cannot give a picture. Nothing here writes
 * Compose state, so it is safe to build inside `remember`.
 */
private class QrCapture(
    private val session: AVCaptureSession,
    private val output: AVCaptureMetadataOutput,
    private val queue: platform.darwin.dispatch_queue_t,
    @Suppress("unused") private val delegate: QrMetadataDelegate,
    val previewLayer: AVCaptureVideoPreviewLayer
) {

    fun dispose() {
        // Clear the delegate first and synchronously, so no code can arrive after this call even
        // while the session is still winding down.
        output.setMetadataObjectsDelegate(null, null)
        // Same serial queue the start went on, so the stop is ordered after it with no race.
        dispatch_async(queue) { session.stopRunning() }
        previewLayer.removeFromSuperlayer()
    }

    companion object {

        fun open(onText: State<(String) -> Unit>): QrCapture? {
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return null
            val input = memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                AVCaptureDeviceInput.deviceInputWithDevice(device = device, error = error.ptr)
            } ?: return null

            val session = AVCaptureSession()
            val output = AVCaptureMetadataOutput()

            // The input and the output have to be paired before the QR type may be asked for or set:
            // which types are available depends on the port the connection is made to.
            session.beginConfiguration()
            if (!session.canAddInput(input)) {
                session.commitConfiguration()
                return null
            }
            session.addInput(input)
            if (!session.canAddOutput(output)) {
                session.commitConfiguration()
                return null
            }
            session.addOutput(output)
            session.commitConfiguration()

            val delegate = QrMetadataDelegate(onText)
            // The main queue is both allowed (not null, and serial) and what we want: the only work
            // per frame is handing one string to the caller, which goes on to touch Compose state.
            output.setMetadataObjectsDelegate(objectsDelegate = delegate, queue = dispatch_get_main_queue())

            // setMetadataObjectTypes throws NSInvalidArgumentException for a type this connection
            // does not offer, and Kotlin/Native does not turn an NSException into a Kotlin one - it
            // ends the process with no Kotlin stack. So ask first. Leaving the types unset is just
            // as wrong the other way: the output then reports nothing at all, and a live picture
            // that decodes nothing reads as a bad QR code.
            val qrOffered = output.availableMetadataObjectTypes.any { it == AVMetadataObjectTypeQRCode }
            if (!qrOffered) {
                output.setMetadataObjectsDelegate(null, null)
                return null
            }
            output.metadataObjectTypes = listOfNotNull(AVMetadataObjectTypeQRCode)

            val previewLayer = AVCaptureVideoPreviewLayer(session = session)
            previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill

            // startRunning blocks, so it must not be on the main thread.
            val queue = dispatch_queue_create("app.aaps.qrscan", null)
            dispatch_async(queue) { session.startRunning() }

            return QrCapture(session, output, queue, delegate, previewLayer)
        }
    }
}

/**
 * The camera picture, handing every QR code it reads to [onScanned].
 *
 * Nothing is done when the app goes to the background and comes back. AVCaptureSession keeps a
 * `startRunning` request across that by itself, and only an explicit `stopRunning` gives it up -
 * so adding lifecycle handling here would be undoing something that already works.
 */
@Composable
internal actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    val currentOnScanned = rememberUpdatedState(onScanned)
    val hasDevice = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null }
    var access by remember {
        mutableStateOf(cameraState(hasDevice, AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)))
    }

    LaunchedEffect(access) {
        if (access == CameraState.Asking) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { allowed ->
                // Apple calls this back on a queue of its choosing, so hop to the main one before
                // touching Compose state.
                dispatch_async(dispatch_get_main_queue()) {
                    access = if (allowed) CameraState.Ready else CameraState.Denied
                }
            }
        }
    }

    // Keyed on access so the screen's own "Try again" path - which takes the scanner out of
    // composition and puts a fresh one back - cannot leave two sessions running at once.
    val capture = remember(access) { if (access == CameraState.Ready) QrCapture.open(currentOnScanned) else null }
    DisposableEffect(capture) { onDispose { capture?.dispose() } }

    when {
        capture != null -> UIKitView(
            factory = { CameraPreviewView(capture.previewLayer) },
            modifier = modifier,
            properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false)
        )

        // The system prompt is on screen; anything behind it is noise. Same as Android.
        access == CameraState.Asking -> Box(modifier)

        else                         -> CameraMessage(access, modifier)
    }
}

/** Says why there is no picture, and offers the one thing that can help where there is one. */
@Composable
private fun CameraMessage(access: CameraState, modifier: Modifier) {
    Box(modifier = modifier.padding(AapsSpacing.xxLarge), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
        ) {
            Text(
                text = stringResource(
                    when (access) {
                        CameraState.Denied  -> SyncStrings.client_setup_scan_camera_denied
                        CameraState.Blocked -> SyncStrings.client_setup_scan_camera_blocked
                        else                -> SyncStrings.client_setup_scan_camera_unavailable
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            // Only where settings can actually fix it. Once iOS has been told no, asking again
            // returns false without showing a prompt, so a "Try again" button here would be a
            // control that visibly does nothing. Restricted is parental controls or a management
            // profile, which the user cannot change at all.
            if (access == CameraState.Denied) {
                Button(onClick = { openAppSettings() }) {
                    Text(stringResource(SyncStrings.client_setup_scan_open_settings))
                }
            }
        }
    }
}

private fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
