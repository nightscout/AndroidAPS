package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetBasalProfilePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SetBasalProfilePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 21
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(opCode.toByte()) + 1.toByte() + basalProfile
        assertEquals(expected.contentToString(), result.contentToString())
    }
}
