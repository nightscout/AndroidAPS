package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetBolusMotorPacketTest : MedtrumTestBase() {

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
        val opCode = 36

        // Call
        val packet = SetBolusMotorPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expectedByteArray = byteArrayOf(opCode.toByte()) + 0.toByte()
        assertEquals(2, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }
}
