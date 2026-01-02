package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import dagger.android.HasAndroidInjector

class MsgError(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0601)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val errorCode = intFromBuff(bytes, 0, 1)
        var errorString = ""
        when (errorCode) {
            1, 2, 3 -> errorString = rh.gs(app.aaps.pump.dana.R.string.pumperror) + " " + errorCode
            4       -> errorString = rh.gs(app.aaps.pump.dana.R.string.pumpshutdown)
            5       -> errorString = rh.gs(app.aaps.pump.dana.R.string.occlusion)
            7       -> errorString = rh.gs(app.aaps.pump.dana.R.string.lowbattery)
            8       -> errorString = rh.gs(app.aaps.pump.dana.R.string.batterydischarged)
        }
        if (errorCode < 8) { // bolus delivering stopped
            danaPump.bolusStopped = true
            rxBus.send(EventOverviewBolusProgress(status = errorString, id = danaPump.bolusingDetailedBolusInfo?.id))
            // at least on Occlusion pump stops communication. Try to force reconnecting
            activePlugin.activePump.disconnect("Error from pump received")
            failed = true
        } else {
            failed = false
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Error detected: $errorString")
        pumpSync.insertAnnouncement(errorString, null, danaPump.pumpType(), danaPump.serialNumber)
    }
}