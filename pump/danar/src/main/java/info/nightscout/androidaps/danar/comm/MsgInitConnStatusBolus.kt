package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.R
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.logging.LTag

class MsgInitConnStatusBolus(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0302)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 > 12) {
            failed = true
            return
        }
        failed = false
        val bolusConfig = intFromBuff(bytes, 0, 1)
        danaPump.isExtendedBolusEnabled = bolusConfig and 0x01 != 0
        danaPump.bolusStep = intFromBuff(bytes, 1, 1) / 100.0
        danaPump.maxBolus = intFromBuff(bytes, 2, 2) / 100.0
        //int bolusRate = intFromBuff(bytes, 4, 8);
        aapsLogger.debug(LTag.PUMPCOMM, "Is Extended bolus enabled: " + danaPump.isExtendedBolusEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus increment: " + danaPump.bolusStep)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus max: " + danaPump.maxBolus)
        if (!danaPump.isExtendedBolusEnabled) {
            uiInteraction.addNotification(Notification.EXTENDED_BOLUS_DISABLED, rh.gs(R.string.danar_enableextendedbolus), Notification.URGENT)
        } else {
            rxBus.send(EventDismissNotification(Notification.EXTENDED_BOLUS_DISABLED))
        }
    }
}