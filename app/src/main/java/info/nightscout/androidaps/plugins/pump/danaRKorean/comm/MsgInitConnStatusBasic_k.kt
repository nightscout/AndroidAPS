package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgInitConnStatusBasic_k(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x0303)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 > 6) {
            return
        }
        danaRPump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1
        val isUtilityEnable = intFromBuff(bytes, 1, 1)
        danaRPump.isEasyModeEnabled = intFromBuff(bytes, 2, 1) == 1
        val easyUIMode = intFromBuff(bytes, 3, 1)
        danaRPump.password = intFromBuff(bytes, 4, 2) xor 0x3463
        aapsLogger.debug(LTag.PUMPCOMM, "isStatusSuspendOn: " + danaRPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "isUtilityEnable: $isUtilityEnable")
        aapsLogger.debug(LTag.PUMPCOMM, "Is EasyUI Enabled: " + danaRPump.isEasyModeEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "easyUIMode: $easyUIMode")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump password: " + danaRPump.password)
        if (danaRPump.isEasyModeEnabled) {
            val notification = Notification(Notification.EASYMODE_ENABLED, resourceHelper.gs(R.string.danar_disableeasymode), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.EASYMODE_ENABLED))
        }
        if (!danaRPump.isPasswordOK) {
            val notification = Notification(Notification.WRONG_PUMP_PASSWORD, resourceHelper.gs(R.string.wrongpumppassword), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONG_PUMP_PASSWORD))
        }
    }
}