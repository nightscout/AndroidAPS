package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ResetRileyLinkConfigurationTask(injector: HasAndroidInjector) : PumpTask(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rfSpy: RFSpy

    override fun run() {
        if (!isRileyLinkDevice) return
        rxBus.send(EventRefreshButtonState(false))
        pumpDevice?.setBusy(true)
        rfSpy.resetRileyLinkConfiguration()
        pumpDevice?.setBusy(false)
        rxBus.send(EventRefreshButtonState(true))
    }
}