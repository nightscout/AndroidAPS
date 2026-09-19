package app.aaps.core.interfaces.pump

import app.aaps.core.data.model.devAssert
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Add command queue to [PluginBaseWithPreferences]
 */
abstract class PumpPluginBase(
    pluginDescription: PluginDescription,
    ownPreferences: List<NonPreferenceKey> = emptyList(),
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    val commandQueue: CommandQueue
) : PluginBaseWithPreferences(pluginDescription, ownPreferences, aapsLogger, rh, preferences) {

    private var initialReadStatusJob: Job? = null

    override suspend fun onStart() {
        super.onStart()
        devAssert(getType() == PluginType.PUMP)
        // Run the initial status read in the background so this onStart() returns immediately.
        // Pump drivers call super.onStart() first and then launch their own async hardware init
        // (e.g. ComboV2 sets up Bluetooth and its pumpManager in a coroutine). If we suspended
        // inline here, the readStatus would fire before that init finished and the connect would
        // fail (pump not yet initialized), leaving the driver stuck until a manual Refresh.
        initialReadStatusJob = pluginScope.launch {
            delay(6000)
            if ((this@PumpPluginBase as? Pump)?.isConfigured() != false)
                commandQueue.readStatus(rh.gs(InterfacesStrings.pump_driver_changed))
        }
    }

    override suspend fun onStop() {
        super.onStop()
        initialReadStatusJob?.cancel()
        initialReadStatusJob = null
    }

    /**
     * The Bluetooth permissions a hardware pump needs, on the platforms that have such a concept.
     *
     * Empty on iOS, where Bluetooth is declared in `Info.plist` rather than requested at runtime -
     * see [bluetoothPermissionGroup].
     */
    override fun requiredPermissions(): List<PermissionGroup> = listOfNotNull(bluetoothPermissionGroup())
}