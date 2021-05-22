package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBasalRate
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BasalProfile
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetActiveBasalRateMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    var activeBasalRate: ActiveBasalRate? = null
        private set

    override fun parse(byteBuf: ByteBuf?) {
        val activeBasalRate = ActiveBasalRate()
        byteBuf?.run {
            activeBasalRate.activeBasalProfile = BasalProfile.fromId(readUInt16LE())
            activeBasalRate.activeBasalProfileName = readUTF16(30)
            activeBasalRate.activeBasalRate = readUInt16Decimal()
        }
        if (activeBasalRate.activeBasalProfile != null) this.activeBasalRate = activeBasalRate
    }
}