package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ResumePumpPacketTest : MedtrumTestBase() {

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
        val opCode = 29

        // Call
        val packet = ResumePumpPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(opCode.toByte(), result[0])
    }
}
