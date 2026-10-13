package app.aaps.core.interfaces.pump

import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences

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

    override fun onStart() {
        super.onStart()
        assert(getType() == PluginType.PUMP)
        handler = Handler(HandlerThread(this::class.java.simpleName + "Handler").also { it.start() }.looper)
        handler?.postDelayed({ commandQueue.readStatus(rh.gs(R.string.pump_driver_changed), null) }, 6000)
    }

    override fun onStop() {
        super.onStop()
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
    }
}