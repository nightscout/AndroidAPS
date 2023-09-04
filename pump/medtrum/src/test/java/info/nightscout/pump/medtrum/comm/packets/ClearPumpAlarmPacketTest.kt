package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toInt
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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
        Assertions.assertEquals(3, result.size)
        Assertions.assertEquals(opCode.toByte(), result[0])
        Assertions.assertEquals(clearCode, result.copyOfRange(1, 3).toInt())
    }
}
