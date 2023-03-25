package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class GetRecordPacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

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
        assertTrue(false)
        // TODO: Implement history and test
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        assertTrue(false)
        // TODO: Implement history and test
    }
}