package info.nightscout.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test

class SetBolusPacketTest : MedtrumTestBase() {

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
        val insulin = 2.35

        // Call
        val packet = SetBolusPacket(packetInjector, insulin)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(19, 1, 47, 0, 0)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }
}
