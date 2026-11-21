package app.aaps.ui.compose.navigation

import app.aaps.core.interfaces.calibration.isCalibrationPluginActive
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.source.CgmDriver
import app.aaps.core.interfaces.source.activeBgSourceOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Centralized check for whether an [app.aaps.core.ui.compose.navigation.ElementType] is available in the current configuration.
 *
 * "Available" means the system supports the action (e.g., the required BG source plugin is enabled).
 * Unavailable elements should be hidden from the UI — if they're never rendered, the route is never called.
 *
 * This is NOT about user preference toggles (e.g., "show carbs button") — those are separate.
 */
@SingleIn(AppScope::class)
@Inject
class ElementAvailability(
    private val activePlugin: ActivePlugin,
    private val config: Config
) {

    fun isAvailable(elementType: ElementType): Boolean = when (elementType) {
        // A client does not know how the master's CGM is calibrated, only the calibration plugin it syncs from the
        // master. So it offers calibration only for that plugin; the master stores it.
        ElementType.CALIBRATION    -> if (config.AAPSCLIENT) isCalibrationOverrideActive() else calibrationOnThisPhone()

        // The driver's own screen, or the vendor app the readings come from. Both are on the master's phone, so a
        // client has none.
        ElementType.CGM            -> !config.AAPSCLIENT && activePlugin.activeBgSourceOrNull().let { it is CgmDriver || it?.appPackages.orEmpty().isNotEmpty() }

        // Patch pumps fill and change the battery in their own activation wizard, so they have no
        // Refill / ReplaceBattery capability. Same rule as the Manage sheet, but without its
        // isInitialized check: this is a capability, not a connection state, and QuickLaunch removes
        // unavailable entries from the saved toolbar for good.
        ElementType.FILL           -> activePlugin.activePump.pumpDescription.isRefillingCapable
        ElementType.BATTERY_CHANGE -> activePlugin.activePump.let { it.pumpDescription.isBatteryReplaceable || it.isBatteryChangeLoggingEnabled() }

        else                       -> true
    }

    /**
     * The BG source's calibration mode decides: it takes calibrations itself, or its app does when AAPS knows the app.
     * A sensor calibrated on the pump has nothing AAPS can open. Else only AAPS's calibration plugin can, when the user
     * turned it on.
     */
    private fun calibrationOnThisPhone(): Boolean = activePlugin.activeBgSourceOrNull().let { source ->
        when (source?.calibrationMode ?: CalibrationMode.AAPS) {
            CalibrationMode.SOURCE  -> true
            CalibrationMode.OWN_APP -> source?.appPackages.orEmpty().isNotEmpty()
            CalibrationMode.PUMP    -> false
            CalibrationMode.AAPS    -> isCalibrationOverrideActive()
        }
    }

    /**
     * True when the active calibration plugin is a real (non-default) override.
     * Used to bypass the user-pref gate on the calibration button: when a real
     * calibration plugin is enabled, the dialog is the only way to feed it
     * fingerstick entries, so the button must remain visible.
     */
    fun isCalibrationOverrideActive(): Boolean = activePlugin.isCalibrationPluginActive()
}
