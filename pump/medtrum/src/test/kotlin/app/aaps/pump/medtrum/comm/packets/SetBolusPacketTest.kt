package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import com.google.common.truth.Truth.assertThat
import app.aaps.core.interfaces.di.MetroMemberInjector
import org.junit.jupiter.api.Test

class SetBolusPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = MetroMemberInjector {
        if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
        }
        true
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val insulin = 2.35

        // Call
        val packet = SetBolusPacket(packetInjector, insulin)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(19, 1, 47, 0, 0)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }
}
