package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.extension.toByteArray
import com.google.common.truth.Truth.assertThat
import app.aaps.core.interfaces.di.MetroMemberInjector
import org.junit.jupiter.api.Test

class SubscribePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = MetroMemberInjector {
        if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
        }
        true
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 4

        // Call
        val packet = SubscribePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(opCode.toByte()) + 4095.toByteArray(2)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }
}
