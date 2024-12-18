package app.aaps.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.packet.PayloadSplitter
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Hex
import org.junit.jupiter.api.Test

class PayloadSplitterTest {

    @Test fun testSplitter() {
        val f1 = "00,01,54,57,10,23,03,00,00,c0,ff,ff,ff,fe,08,20,2e,a8,50,30".replace(",", "")
        val f2 = "01,04,bc,20,1f,f6,3d,00,01,a5,ff,ff,ff,fe,08,20,2e,a8,50,30".replace(",", "")
        val payload = Hex.decode("54,57,10,23,03,00,00,c0,ff,ff,ff,fe,08,20,2e,a8,50,30,3d,00,01,a5".replace(",", ""))

        val splitter = PayloadSplitter(payload)
        val packets = splitter.splitInPackets()

        assertThat(packets).hasSize(2)
        assertThat(packets[0].toByteArray().toHex()).isEqualTo(f1)
        val p2 = packets[1].toByteArray()
        assertThat(p2.size).isAtLeast(10)
        assertThat(p2.copyOfRange(0, 10).toHex()).isEqualTo(f2.subSequence(0, 20))
    }
}
