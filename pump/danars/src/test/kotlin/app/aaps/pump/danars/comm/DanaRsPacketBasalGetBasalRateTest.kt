package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DanaRsPacketBasalGetBasalRateTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketBasalGetBasalRate(aapsLogger, uiInteraction, rxBus, rh, danaPump)
        // test message decoding
        // rate is 0.01
        val array = ByteArray(100)
        putIntToArray(array, 0, (1.0 * 100).toInt())
        putByteToArray(array, 2, (0.05 * 100).toInt().toByte())
        packet.handleMessage(array)
        assertThat(danaPump.maxBasal).isWithin(0.001).of(1.0)
        assertThat(danaPump.basalStep).isWithin(0.001).of(0.05)
        assertThat(packet.failed).isTrue()
        assertThat(packet.friendlyName).isEqualTo("BASAL__GET_BASAL_RATE")
    }
}
