package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetBolusPacketTest : MedtrumTestBase() {

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
        val insulin = 2.35

        // Call
        val packet = SetBolusPacket(packetInjector, insulin)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(19, 1, 47, 0, 0)
        assertEquals(expected.contentToString(), result.contentToString())
    }
}
