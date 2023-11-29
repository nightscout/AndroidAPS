package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgSettingBasalProfileAll
import org.junit.jupiter.api.Test

class MsgSettingBasalProfileAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingBasalProfileAll(injector)

        // test message decoding
        packet.handleMessage(createArray(400, 1.toByte()))
    }
}