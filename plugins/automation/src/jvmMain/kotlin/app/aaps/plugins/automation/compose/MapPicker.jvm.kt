package app.aaps.plugins.automation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource

/**
 * No map picker on desktop. Drawing one means a desktop map toolkit, which nothing here wires up.
 *
 * [isMapPickerAvailable] is false, so the button that leads here is hidden and this screen is not
 * reachable in normal use. It still says so plainly rather than drawing an empty box, because an
 * empty screen would look like a map that failed to load.
 */
@Composable
actual fun MapPickerScreen(
    initialLat: Double?,
    initialLon: Double?,
    onLocationTapped: (Double, Double) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(CoreUiStrings.not_available_full))
    }
}

actual val isMapPickerAvailable: Boolean = false
