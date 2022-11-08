package info.nightscout.androidaps.interfaces

import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.interfaces.PluginDescription
import info.nightscout.interfaces.PluginType
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