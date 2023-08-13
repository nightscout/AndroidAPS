package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SubscribePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 4

        // Call
        val packet = SubscribePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expectedByteArray = byteArrayOf(opCode.toByte()) + 4095.toByteArray(2)
        assertEquals(3, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }
}
