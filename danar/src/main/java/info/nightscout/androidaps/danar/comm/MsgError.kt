package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.R
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress

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
            1, 2, 3 -> errorString = rh.gs(R.string.pumperror) + " " + errorCode
            4       -> errorString = rh.gs(R.string.pumpshutdown)
            5       -> errorString = rh.gs(R.string.occlusion)
            7       -> errorString = rh.gs(R.string.lowbattery)
            8       -> errorString = rh.gs(R.string.batterydischarged)
        }
        if (errorCode < 8) { // bolus delivering stopped
            val bolusingEvent = EventOverviewBolusProgress
            danaPump.bolusStopped = true
            bolusingEvent.status = errorString
            rxBus.send(bolusingEvent)
            failed = true
        } else {
            failed = false
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Error detected: $errorString")
        pumpSync.insertAnnouncement(errorString, null, danaPump.pumpType(), danaPump.serialNumber)
    }
}