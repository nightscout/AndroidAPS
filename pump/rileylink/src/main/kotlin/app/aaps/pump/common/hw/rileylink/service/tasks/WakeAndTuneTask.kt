package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import javax.inject.Inject

class WakeAndTuneTask @Inject constructor(activePlugin: ActivePlugin, private val rxBus: RxBus) : PumpTask(activePlugin) {

    override fun run() {
        rxBus.send(EventRefreshButtonState(false))
        pumpDevice?.setBusy(true)
        pumpDevice?.rileyLinkService?.doTuneUpDevice()
        pumpDevice?.setBusy(false)
        rxBus.send(EventRefreshButtonState(true))
    }
}