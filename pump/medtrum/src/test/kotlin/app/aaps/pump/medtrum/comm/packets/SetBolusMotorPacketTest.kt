package app.aaps.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test

class SetBolusMotorPacketTest : MedtrumTestBase() {

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
        val opCode = 36

        // Call
        val packet = SetBolusMotorPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertThat(result).asList().containsExactly(opCode.toByte(), 0.toByte()).inOrder()
    }
}
