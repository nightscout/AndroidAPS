package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgSettingMeal(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump,
    private val danaRKoreanPlugin: DanaRKoreanPlugin
) : MessageBase() {

    init {
        SetCommand(0x3203)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.basalStep = intFromBuff(bytes, 0, 1) / 100.0
        danaRPump.bolusStep = intFromBuff(bytes, 1, 1) / 100.0
        val bolusEnabled = intFromBuff(bytes, 2, 1) == 1
        val melodyTime = intFromBuff(bytes, 3, 1)
        val blockTime = intFromBuff(bytes, 4, 1)
        danaRPump.isConfigUD = intFromBuff(bytes, 5, 1) == 1
        aapsLogger.debug(LTag.PUMPCOMM, "Basal step: " + danaRPump.basalStep)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: " + danaRPump.bolusStep)
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus enabled: $bolusEnabled")
        aapsLogger.debug(LTag.PUMPCOMM, "Melody time: $melodyTime")
        aapsLogger.debug(LTag.PUMPCOMM, "Block time: $blockTime")
        aapsLogger.debug(LTag.PUMPCOMM, "Is Config U/d: " + danaRPump.isConfigUD)
        // DanaRKorean is not possible to set to 0.01 but it works when controlled from AAPS
        if (danaRKoreanPlugin.isEnabled(PluginType.PUMP)) {
            danaRPump.basalStep = 0.01
        }
        if (danaRPump.basalStep != 0.01) {
            val notification = Notification(Notification.WRONGBASALSTEP, resourceHelper.gs(R.string.danar_setbasalstep001), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONGBASALSTEP))
        }
        if (danaRPump.isConfigUD) {
            val notification = Notification(Notification.UD_MODE_ENABLED, resourceHelper.gs(R.string.danar_switchtouhmode), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.UD_MODE_ENABLED))
        }
    }
}