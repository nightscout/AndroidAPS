package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import dagger.android.HasAndroidInjector

class MsgBolusStop(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0101)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "Message received")
        danaPump.bolusStopped = true
        if (!danaPump.bolusStopForced) {
            BolusProgressData.delivered = BolusProgressData.insulin
            rxBus.send(EventOverviewBolusProgress(rh, percent = 100, id = danaPump.bolusingDetailedBolusInfo?.id))
        }
        else rxBus.send(EventOverviewBolusProgress(status = rh.gs(app.aaps.pump.dana.R.string.overview_bolusprogress_stoped), id = danaPump.bolusingDetailedBolusInfo?.id))
    }
}