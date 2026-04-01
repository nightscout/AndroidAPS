package app.aaps.ui.compose.navigation

import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.ui.compose.navigation.ElementType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized check for whether an [app.aaps.core.ui.compose.navigation.ElementType] is available in the current configuration.
 *
 * "Available" means the system supports the action (e.g., the required BG source plugin is enabled).
 * Unavailable elements should be hidden from the UI — if they're never rendered, the route is never called.
 *
 * This is NOT about user preference toggles (e.g., "show carbs button") — those are separate.
 */
@Singleton
class ElementAvailability @Inject constructor(
    private val xDripSource: XDripSource,
    private val dexcomBoyda: DexcomBoyda
) {

    fun isAvailable(elementType: ElementType): Boolean = when (elementType) {
        ElementType.CALIBRATION,
        ElementType.CGM_XDRIP   -> xDripSource.isEnabled()
        ElementType.CGM_DEX     -> dexcomBoyda.isEnabled()

        else                    -> true
    }
}
