package app.aaps.pump.common.hw.rileylink.service.tasks

import dagger.android.HasAndroidInjector

class DiscoverGattServicesTask(injector: HasAndroidInjector, private val needToConnect: Boolean = false) : ServiceTask(injector) {

    override fun run() {
        if (needToConnect) pumpDevice?.rileyLinkService?.rileyLinkBLE?.connectGatt()
        pumpDevice?.rileyLinkService?.rileyLinkBLE?.discoverServices()
    }
}