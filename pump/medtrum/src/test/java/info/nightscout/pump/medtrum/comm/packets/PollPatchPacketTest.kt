package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PollPatchPacketTest : MedtrumTestBase() {

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
        val opCode = 30

        // Call
        val packet = PollPatchPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(opCode.toByte(), result[0])
    }
}
