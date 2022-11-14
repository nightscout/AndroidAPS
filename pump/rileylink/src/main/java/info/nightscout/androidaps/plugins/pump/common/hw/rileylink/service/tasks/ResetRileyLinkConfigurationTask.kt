package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshButtonState
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