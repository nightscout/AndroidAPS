package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import com.google.common.truth.Truth.assertThat
import app.aaps.core.interfaces.di.MetroMemberInjector
import org.junit.jupiter.api.Test

class CancelBolusPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = MetroMemberInjector {
        if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
        }
        true
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 20

        // Call
        val packet = CancelBolusPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertThat(result).asList().containsExactly(opCode.toByte(), 1.toByte()).inOrder()
    }
}
