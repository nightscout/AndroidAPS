package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import dagger.android.HasAndroidInjector

class MsgSettingMeal(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x3203)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.basalStep = intFromBuff(bytes, 0, 1) / 100.0
        danaPump.bolusStep = intFromBuff(bytes, 1, 1) / 100.0
        val bolusEnabled = intFromBuff(bytes, 2, 1) == 1
        val melodyTime = intFromBuff(bytes, 3, 1)
        val blockTime = intFromBuff(bytes, 4, 1)
        danaPump.isConfigUD = intFromBuff(bytes, 5, 1) == 1
        aapsLogger.debug(LTag.PUMPCOMM, "Basal step: " + danaPump.basalStep)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: " + danaPump.bolusStep)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus enabled: $bolusEnabled")
        aapsLogger.debug(LTag.PUMPCOMM, "Melody time: $melodyTime")
        aapsLogger.debug(LTag.PUMPCOMM, "Block time: $blockTime")
        aapsLogger.debug(LTag.PUMPCOMM, "Is Config U/d: " + danaPump.isConfigUD)
        // DanaRKorean is not possible to set to 0.01 but it works when controlled from AAPS
        if (danaRKoreanPlugin.isEnabled()) {
            danaPump.basalStep = 0.01
        }
        if (danaPump.basalStep != 0.01) {
            notificationManager.post(NotificationId.WRONG_BASAL_STEP, app.aaps.pump.dana.R.string.danar_setbasalstep001, level = NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.WRONG_BASAL_STEP)
        }
        if (danaPump.isConfigUD) {
            notificationManager.post(NotificationId.UD_MODE_ENABLED, app.aaps.pump.dana.R.string.danar_switchtouhmode)
        } else {
            notificationManager.dismiss(NotificationId.UD_MODE_ENABLED)
        }
    }
}