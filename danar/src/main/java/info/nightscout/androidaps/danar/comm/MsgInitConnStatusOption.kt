package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification

class MsgInitConnStatusOption(
    injector: HasAndroidInjector
) : MessageBase(injector) {

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
            danaPump.password = intFromBuff(bytes, 9, 2) xor 0x3463
            aapsLogger.debug(LTag.PUMPCOMM, "Pump password: " + danaPump.password)
        } else {
            failed = true
        }
        if (!danaPump.isPasswordOK) {
            val notification = Notification(Notification.WRONG_PUMP_PASSWORD, resourceHelper.gs(R.string.wrongpumppassword), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONG_PUMP_PASSWORD))
        }
        // This is last message of initial sequence
        activePlugin.activePump.finishHandshaking()
    }
}