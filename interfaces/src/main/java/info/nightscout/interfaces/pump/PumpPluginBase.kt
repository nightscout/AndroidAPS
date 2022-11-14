package info.nightscout.interfaces.pump

import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.R
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper

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