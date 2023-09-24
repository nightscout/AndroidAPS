package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.logging.AAPSLogger
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class DiscoverGattServicesTask(injector: HasAndroidInjector, private val needToConnect: Boolean = false) : ServiceTask(injector) {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun run() {
        if (needToConnect) pumpDevice?.rileyLinkService?.rileyLinkBLE?.connectGatt()
        pumpDevice?.rileyLinkService?.rileyLinkBLE?.discoverServices()
    }
}