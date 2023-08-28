package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toInt
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class ClearPumpAlarmPacketTest : MedtrumTestBase() {

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
        val opCode = 115
        val clearCode = 4

        // Call
        val packet = ClearPumpAlarmPacket(packetInjector, clearCode)
        val result = packet.getRequest()

        // Expected values
        assertEquals(3, result.size)
        assertEquals(opCode.toByte(), result[0])
        assertEquals(clearCode, result.copyOfRange(1, 3).toInt())
    }
}
