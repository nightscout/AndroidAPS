package app.aaps.core.interfaces.pump

import android.Manifest
import android.annotation.SuppressLint
import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
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
    ownPreferences: List<Class<out NonPreferenceKey>> = emptyList(),
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    val commandQueue: CommandQueue
) : PluginBaseWithPreferences(pluginDescription, ownPreferences, aapsLogger, rh, preferences) {

    var handler: Handler? = null
    private var initialReadStatusJob: Job? = null

    override suspend fun onStart() {
        super.onStart()
        assert(getType() == PluginType.PUMP)
        handler = Handler(HandlerThread(this::class.java.simpleName + "Handler").also { it.start() }.looper)
        // Run the initial status read in the background so this onStart() returns immediately.
        // Pump drivers call super.onStart() first and then launch their own async hardware init
        // (e.g. ComboV2 sets up Bluetooth and its pumpManager in a coroutine). If we suspended
        // inline here, the readStatus would fire before that init finished and the connect would
        // fail (pump not yet initialized), leaving the driver stuck until a manual Refresh.
        initialReadStatusJob = pluginScope.launch {
            delay(6000)
            if ((this@PumpPluginBase as? Pump)?.isConfigured() != false)
                commandQueue.readStatus(rh.gs(R.string.pump_driver_changed))
        }
    }

    override suspend fun onStop() {
        super.onStop()
        initialReadStatusJob?.cancel()
        initialReadStatusJob = null
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
    }

    @SuppressLint("InlinedApi")
    override fun requiredPermissions(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
            rationaleTitle = R.string.permission_bluetooth_title,
            rationaleDescription = R.string.permission_bluetooth_description,
        )
    )
}