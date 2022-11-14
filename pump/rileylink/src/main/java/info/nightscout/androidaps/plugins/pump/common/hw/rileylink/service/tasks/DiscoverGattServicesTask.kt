package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.AAPSLogger
import javax.inject.Inject

class DiscoverGattServicesTask(injector: HasAndroidInjector, private val needToConnect: Boolean = false) : ServiceTask(injector) {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun run() {
        if (needToConnect) pumpDevice?.rileyLinkService?.rileyLinkBLE?.connectGatt()
        pumpDevice?.rileyLinkService?.rileyLinkBLE?.discoverServices()
    }
}