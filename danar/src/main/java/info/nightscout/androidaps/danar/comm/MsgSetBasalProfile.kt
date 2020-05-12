package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgSetBasalProfile(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    index: Byte,
    values: Array<Double>
) : MessageBase() {

    // index 0-3
    init {
        SetCommand(0x3306)
        AddParamByte(index)
        for (i in 0..23) {
            AddParamInt((values[i] * 100).toInt())
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Set basal profile: $index")
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set basal profile result: $result FAILED!!!")
            val reportFail = Notification(Notification.PROFILE_SET_FAILED, resourceHelper.gs(R.string.profile_set_failed), Notification.URGENT)
            rxBus.send(EventNewNotification(reportFail))
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set basal profile result: $result")
            val reportOK = Notification(Notification.PROFILE_SET_OK, resourceHelper.gs(R.string.profile_set_ok), Notification.INFO, 60)
            rxBus.send(EventNewNotification(reportOK))
        }
    }
}