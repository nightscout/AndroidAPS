package app.aaps.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.packet.PayloadJoiner
import app.aaps.pump.omnipod.dash.driver.comm.packet.PayloadSplitter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Random

class PayloadSplitJoinTest {

    private val random = Random(42)

    @Test fun testSplitAndJoinBack() {
        for (s in 0..250) {
            val payload = ByteArray(s)
            random.nextBytes(payload)
            val splitter = PayloadSplitter(payload)
            val packets = splitter.splitInPackets()
            val joiner = PayloadJoiner(packets[0].toByteArray())
            for (p in packets.subList(1, packets.size)) {
                joiner.accumulate(p.toByteArray())
            }
            val got = joiner.finalize()
            assertThat(got.toHex()).isEqualTo(payload.toHex())
        }
    }
}
