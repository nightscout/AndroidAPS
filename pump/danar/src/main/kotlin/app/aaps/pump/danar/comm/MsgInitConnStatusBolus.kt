package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import dagger.android.HasAndroidInjector

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
            uiInteraction.addNotification(Notification.EXTENDED_BOLUS_DISABLED, rh.gs(app.aaps.pump.dana.R.string.danar_enableextendedbolus), Notification.URGENT)
        } else {
            rxBus.send(EventDismissNotification(Notification.EXTENDED_BOLUS_DISABLED))
        }
    }
}