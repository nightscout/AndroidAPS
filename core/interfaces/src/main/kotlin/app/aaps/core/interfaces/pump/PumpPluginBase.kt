package app.aaps.core.interfaces.pump

import android.os.SystemClock
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.HasAndroidInjector

abstract class PumpPluginBase(
    pluginDescription: PluginDescription,
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    val commandQueue: CommandQueue
) : PluginBase(pluginDescription, aapsLogger, rh, injector) {

    override fun onStart() {
        super.onStart()
        if (getType() == PluginType.PUMP) {
            Thread {
                SystemClock.sleep(3000)
                commandQueue.readStatus(rh.gs(R.string.pump_driver_changed), null)
            }.start()
        }
    }
}