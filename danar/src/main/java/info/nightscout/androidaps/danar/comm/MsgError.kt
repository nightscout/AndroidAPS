package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgError(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaPump: DanaPump,
    private val nsUpload: NSUpload
) : MessageBase() {

    init {
        SetCommand(0x0601)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val errorCode = intFromBuff(bytes, 0, 1)
        var errorString = ""
        when (errorCode) {
            1, 2, 3 -> errorString = resourceHelper.gs(R.string.pumperror) + " " + errorCode
            4       -> errorString = resourceHelper.gs(R.string.pumpshutdown)
            5       -> errorString = resourceHelper.gs(R.string.occlusion)
            7       -> errorString = resourceHelper.gs(R.string.lowbattery)
            8       -> errorString = resourceHelper.gs(R.string.batterydischarged)
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
        nsUpload.uploadError(errorString)
    }
}