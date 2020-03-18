package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgInitConnStatusOption(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump,
    private val activePlugin: ActivePluginProvider
) : MessageBase() {

    init {
        SetCommand(0x0304)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        //val status1224Clock = intFromBuff(bytes, 0, 1)
        //val isStatusButtonScroll = intFromBuff(bytes, 1, 1)
        //val soundVibration = intFromBuff(bytes, 2, 1)
        //val glucoseUnit = intFromBuff(bytes, 3, 1)
        //val lcdTimeout = intFromBuff(bytes, 4, 1)
        //val backlightgTimeout = intFromBuff(bytes, 5, 1)
        //val languageOption = intFromBuff(bytes, 6, 1)
        //val lowReservoirAlarmBoundary = intFromBuff(bytes, 7, 1)
        //int none = intFromBuff(bytes, 8, 1);
        if (bytes.size >= 21) {
            failed = false
            danaRPump.password = intFromBuff(bytes, 9, 2) xor 0x3463
            aapsLogger.debug(LTag.PUMPCOMM, "Pump password: " + danaRPump.password)
        } else {
            failed = true
        }
        if (!danaRPump.isPasswordOK) {
            val notification = Notification(Notification.WRONG_PUMP_PASSWORD, resourceHelper.gs(R.string.wrongpumppassword), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONG_PUMP_PASSWORD))
        }
        // This is last message of initial sequence
        activePlugin.activePump.finishHandshaking()
    }
}