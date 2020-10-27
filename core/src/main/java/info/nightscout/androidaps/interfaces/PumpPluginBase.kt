package info.nightscout.androidaps.interfaces

import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper

abstract class PumpPluginBase(
    pluginDescription: PluginDescription,
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    val commandQueue: CommandQueueProvider
) : PluginBase(pluginDescription, aapsLogger, resourceHelper, injector) {

    override fun onStart() {
        super.onStart()
        if (getType() == PluginType.PUMP) {
            Thread(Runnable {
                SystemClock.sleep(3000)
                commandQueue.readStatus("Pump driver changed.", null)
            }).start()
        }
    }
}