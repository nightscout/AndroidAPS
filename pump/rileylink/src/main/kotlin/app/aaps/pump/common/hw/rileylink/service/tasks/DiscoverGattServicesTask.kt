package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.plugin.ActivePlugin
import javax.inject.Inject

class DiscoverGattServicesTask @Inject constructor(
    activePlugin: ActivePlugin
) : ServiceTask(activePlugin) {

    private var needToConnect: Boolean = false

    fun with(needToConnect: Boolean): DiscoverGattServicesTask {
        this.needToConnect = needToConnect
        return this
    }

    override fun run() {
        if (needToConnect) pumpDevice?.rileyLinkService?.rileyLinkBLE?.connectGatt()
        pumpDevice?.rileyLinkService?.rileyLinkBLE?.discoverServices()
    }
}