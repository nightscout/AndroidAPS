package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.dexcomoneplus.identity.OnePlusGs1ApplicatorParser
import app.aaps.plugins.source.SourceStrings

/**
 * Reads the Data Matrix on a Dexcom ONE+ / G7 applicator with the camera.
 *
 * The dialog and the check of the text are here; the camera itself is [CameraApplicatorScanner],
 * a CameraX preview read by ZXing. A caller that passes no scanner (the screen tests) gets no scan
 * button: the code is typed, as before.
 */
interface OnePlusApplicatorScanner {

    /**
     * The camera view, filling [modifier].
     *
     * @param onScanned called with every decoded text. It returns true when the text was a valid
     *   applicator code, and the scanner then stops reading.
     */
    @Composable
    fun CameraView(onScanned: (String) -> Boolean, modifier: Modifier)
}

/**
 * Accepts [text] as an applicator code only when the driver's parser can read it.
 *
 * Any other barcode in front of the camera (a box label, a QR code) is ignored, so the field is
 * never filled with something that is not a pairing code.
 */
internal fun acceptApplicatorScan(text: String): Boolean = OnePlusGs1ApplicatorParser.parse(text) != null

/** The camera, a short hint and a way out, over the start screen. */
@Composable
internal fun OnePlusApplicatorScanDialog(
    scanner: OnePlusApplicatorScanner,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AapsSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            ) {
                Text(
                    text = stringResource(SourceStrings.dexcom_oneplus_applicator_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    scanner.CameraView(
                        onScanned = { text ->
                            val ok = acceptApplicatorScan(text)
                            if (ok) onCode(text)
                            ok
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CoreUiStrings.cancel))
                }
            }
        }
    }
}
