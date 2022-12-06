package info.nightscout.androidaps.danaRKorean.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.logging.LTag

class MsgInitConnStatusBasic_k(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0303)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 > 6) {
            return
        }
        danaPump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1
        val isUtilityEnable = intFromBuff(bytes, 1, 1)
        danaPump.isEasyModeEnabled = intFromBuff(bytes, 2, 1) == 1
        val easyUIMode = intFromBuff(bytes, 3, 1)
        danaPump.password = intFromBuff(bytes, 4, 2) xor 0x3463
        aapsLogger.debug(LTag.PUMPCOMM, "isStatusSuspendOn: " + danaPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "isUtilityEnable: $isUtilityEnable")
        aapsLogger.debug(LTag.PUMPCOMM, "Is EasyUI Enabled: " + danaPump.isEasyModeEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "easyUIMode: $easyUIMode")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump password: " + danaPump.password)
        if (danaPump.isEasyModeEnabled) {
            uiInteraction.addNotification(Notification.EASY_MODE_ENABLED, rh.gs(info.nightscout.pump.dana.R.string.danar_disableeasymode), Notification.URGENT)
        } else {
            rxBus.send(EventDismissNotification(Notification.EASY_MODE_ENABLED))
        }
        if (!danaPump.isPasswordOK) {
            uiInteraction.addNotification(Notification.WRONG_PUMP_PASSWORD, rh.gs(info.nightscout.pump.dana.R.string.wrongpumppassword), Notification.URGENT)
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONG_PUMP_PASSWORD))
        }
    }
}