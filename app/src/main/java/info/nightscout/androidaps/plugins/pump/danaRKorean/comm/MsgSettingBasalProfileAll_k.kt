package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase
import java.util.*

/**
 * Created by mike on 05.07.2016.
 *
 *
 *
 *
 * THIS IS BROKEN IN PUMP... SENDING ONLY 1 PROFILE
 */
class MsgSettingBasalProfileAll_k(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x3206)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaRPump.pumpProfiles = Array(4) { Array(48) { 0.0 } }
        if (danaRPump.basal48Enable) {
            for (profile in 0..3) {
                val position = intFromBuff(bytes, 107 * profile, 1)
                for (index in 0..47) {
                    var basal = intFromBuff(bytes, 107 * profile + 2 * index + 1, 2)
                    if (basal < 10) basal = 0
                    danaRPump.pumpProfiles!![position][index] = basal / 100 / 24.0 // in units/day
                }
            }
        } else {
            for (profile in 0..3) {
                val position = intFromBuff(bytes, 49 * profile, 1)
                aapsLogger.debug(LTag.PUMPCOMM, "position $position")
                for (index in 0..23) {
                    var basal = intFromBuff(bytes, 59 * profile + 2 * index + 1, 2)
                    if (basal < 10) basal = 0
                    aapsLogger.debug(LTag.PUMPCOMM, "position $position index $index")
                    danaRPump.pumpProfiles!![position][index] = basal / 100 / 24.0 // in units/day
                }
            }
        }
        if (danaRPump.basal48Enable) {
            for (profile in 0..3) {
                for (index in 0..23) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaRPump.pumpProfiles!![profile][index])
                }
            }
        } else {
            for (profile in 0..3) {
                for (index in 0..47) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index / 2) +
                        ":" + String.format(Locale.ENGLISH, "%02d", index % 2 * 30) + " : " +
                        danaRPump.pumpProfiles!![profile][index])
                }
            }
        }
    }
}