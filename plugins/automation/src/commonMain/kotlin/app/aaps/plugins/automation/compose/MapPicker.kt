package app.aaps.plugins.automation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Lets the user pick a position for a location trigger by tapping a map.
 *
 * Platform specific because every platform draws maps with its own toolkit - Android uses osmdroid,
 * an Apple target would use MapKit. Only the picking is platform work; what the trigger does with the
 * position stays in shared code.
 *
 * Do not call this without checking [isMapPickerAvailable] first. A target that cannot draw a map
 * shows a short "not available" note instead, and the button that leads here is hidden, so the user
 * never reaches a screen that cannot do its job.
 *
 * @param initialLat starting latitude, or null to let the map choose its own start
 * @param initialLon starting longitude, or null to let the map choose its own start
 * @param onLocationTapped called with latitude and longitude each time the user taps the map
 */
@Composable
expect fun MapPickerScreen(
    initialLat: Double?,
    initialLon: Double?,
    onLocationTapped: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
)

/**
 * Whether this platform can actually show [MapPickerScreen].
 *
 * False means the location trigger has to be set from the current position instead, so the UI hides
 * the "pick from map" button rather than offering a button that leads nowhere.
 */
expect val isMapPickerAvailable: Boolean
