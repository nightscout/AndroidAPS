package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import com.google.crypto.tink.subtle.Hex
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.PayloadSplitter
import info.nightscout.core.utils.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

class PayloadSplitterTest {

    @Test fun testSplitter() {
        val f1 = "00,01,54,57,10,23,03,00,00,c0,ff,ff,ff,fe,08,20,2e,a8,50,30".replace(",", "")
        val f2 = "01,04,bc,20,1f,f6,3d,00,01,a5,ff,ff,ff,fe,08,20,2e,a8,50,30".replace(",", "")
        val payload = Hex.decode("54,57,10,23,03,00,00,c0,ff,ff,ff,fe,08,20,2e,a8,50,30,3d,00,01,a5".replace(",", ""))

        val splitter = PayloadSplitter(payload)
        val packets = splitter.splitInPackets()

        assertEquals(packets.size, 2)
        assertEquals(f1, packets.get(0).toByteArray().toHex())
        val p2 = packets.get(1).toByteArray()
        assertTrue(p2.size >= 10)
        assertEquals(f2.subSequence(0, 20), p2.copyOfRange(0, 10).toHex())
    }
}
