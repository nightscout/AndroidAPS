package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class GetRecordPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is GetRecordPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val recordIndex = 4
        medtrumPump.patchId = 146

        // Call
        val packet = GetRecordPacket(packetInjector, recordIndex)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(99, 4, 0, -110, 0)
        assertEquals(expected.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val data = byteArrayOf(35, 99, 9, 1, 0, 0, -86, 28, 2, -1, -5, -40, -27, -18, 14, 0, -64, 1, -91, -20, -82, 17, -91, -20, -82, 17, 1, 0, 26, 0, 0, 0, -102, 0, -48)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        assertEquals(true, result)
        assertEquals(false, packet.failed)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val data = byteArrayOf(35, 99, 9, 1, 0, 0, -86, 28, 2, -1, -5, -40, -27, -18, 14, 0, -64)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        assertEquals(false, result)
        assertEquals(true, packet.failed)
    }
}
