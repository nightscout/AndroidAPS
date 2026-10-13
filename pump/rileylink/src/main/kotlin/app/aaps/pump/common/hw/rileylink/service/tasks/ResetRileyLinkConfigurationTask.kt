package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import javax.inject.Inject

class ResetRileyLinkConfigurationTask @Inject constructor(activePlugin: ActivePlugin, private val rxBus: RxBus, private val rfSpy: RFSpy) : PumpTask(activePlugin) {

    override fun run() {
        if (!isRileyLinkDevice) return
        rxBus.send(EventRefreshButtonState(false))
        pumpDevice?.setBusy(true)
        rfSpy.resetRileyLinkConfiguration()
        pumpDevice?.setBusy(false)
        rxBus.send(EventRefreshButtonState(true))
    }
}