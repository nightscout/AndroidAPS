package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification

class MsgSettingMeal(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        SetCommand(0x3203)
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
        if (danaRKoreanPlugin.isEnabled(PluginType.PUMP)) {
            danaPump.basalStep = 0.01
        }
        if (danaPump.basalStep != 0.01) {
            val notification = Notification(Notification.WRONGBASALSTEP, resourceHelper.gs(R.string.danar_setbasalstep001), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONGBASALSTEP))
        }
        if (danaPump.isConfigUD) {
            val notification = Notification(Notification.UD_MODE_ENABLED, resourceHelper.gs(R.string.danar_switchtouhmode), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.UD_MODE_ENABLED))
        }
    }
}