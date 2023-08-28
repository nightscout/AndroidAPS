package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class CancelBolusPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 20

        // Call
        val packet = CancelBolusPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expectedByteArray = byteArrayOf(opCode.toByte()) + 1.toByte()
        assertEquals(2, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }
}
