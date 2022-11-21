package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks

import dagger.android.HasAndroidInjector
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshButtonState
import javax.inject.Inject

class WakeAndTuneTask(injector: HasAndroidInjector) : PumpTask(injector) {

    @Inject lateinit var rxBus: RxBus

    override fun run() {
        rxBus.send(EventRefreshButtonState(false))
        pumpDevice?.setBusy(true)
        pumpDevice?.rileyLinkService?.doTuneUpDevice()
        pumpDevice?.setBusy(false)
        rxBus.send(EventRefreshButtonState(true))
    }
}