package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingActiveProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingActiveProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingActiveProfile(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(packet.intFromBuff(createArray(34, 7.toByte()), 0, 1), danaPump.activeProfile)
    }
}