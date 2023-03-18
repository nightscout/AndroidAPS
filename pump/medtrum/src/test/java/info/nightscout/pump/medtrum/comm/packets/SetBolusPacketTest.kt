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
        val opCode = 19

        // Call
        val packet = SetBolusPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        // TODO correct value's
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
    }
}
