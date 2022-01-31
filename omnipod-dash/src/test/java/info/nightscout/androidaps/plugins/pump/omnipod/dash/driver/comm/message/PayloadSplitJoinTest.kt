package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.extensions.toHex
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.PayloadJoiner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.PayloadSplitter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class PayloadSplitJoinTest {

    private val random = Random(42)

    @Test fun testSplitAndJoinBack() {
        for (s in 0..250) {
            val payload = ByteArray(s)
            random.nextBytes(payload)
            val splitter = PayloadSplitter(payload)
            val packets = splitter.splitInPackets()
            val joiner = PayloadJoiner(packets.get(0).toByteArray())
            for (p in packets.subList(1, packets.size)) {
                joiner.accumulate(p.toByteArray())
            }
            val got = joiner.finalize()
            assertEquals(got.toHex(), payload.toHex())
        }
    }
}
