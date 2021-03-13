package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadJoiner
import info.nightscout.androidaps.utils.extensions.toHex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class PayloadSplitJoinTest {

    val random = Random(42)
    @Test fun testSplitAndJoinBack() {
        for (s in 0..250) {
            val payload = ByteArray(s)
            random.nextBytes(payload)
            val splitter = PayloadSplitter(payload)
            val packets = splitter.splitInPackets()
            val joiner = PayloadJoiner(packets.get(0).asByteArray())
            for (p in packets.subList(1, packets.size)) {
                joiner.accumulate(p.asByteArray())
            }
            val got = joiner.finalize()
            assertEquals(got.toHex(), payload.toHex())
        }
    }
}