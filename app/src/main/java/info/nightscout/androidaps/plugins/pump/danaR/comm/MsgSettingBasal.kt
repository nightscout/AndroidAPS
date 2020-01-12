package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import java.util.*

class MsgSettingBasal(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val danaRPlugin: DanaRPlugin
) : MessageBase() {

    init {
        SetCommand(0x3202)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.pumpProfiles =  Array(4) {Array(48) {0.0} }
        for (index in 0..23) {
            var basal = intFromBuff(bytes, 2 * index, 2)
            if (basal < danaRPlugin.pumpDescription.basalMinimumRate) basal = 0
            danaRPump.pumpProfiles!![danaRPump.activeProfile][index] = basal / 100.0
        }
        for (index in 0..23) {
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaRPump.pumpProfiles!![danaRPump.activeProfile][index])
        }
    }
}