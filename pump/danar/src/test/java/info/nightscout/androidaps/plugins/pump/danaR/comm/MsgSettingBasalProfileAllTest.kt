package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingBasalProfileAll
import org.junit.jupiter.api.Test

class MsgSettingBasalProfileAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingBasalProfileAll(injector)

        // test message decoding
        packet.handleMessage(createArray(400, 1.toByte()))
    }
}