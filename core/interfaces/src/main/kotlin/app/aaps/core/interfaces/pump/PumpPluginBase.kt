package app.aaps.core.interfaces.pump

import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper

abstract class PumpPluginBase(
    pluginDescription: PluginDescription,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    val commandQueue: CommandQueue
) : PluginBase(pluginDescription, aapsLogger, rh) {

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
        handler = null
    }
}