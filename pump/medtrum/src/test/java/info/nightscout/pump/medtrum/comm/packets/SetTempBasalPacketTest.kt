package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetTempBasalPacketTest : MedtrumTestBase() {

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
        val opCode = 24

        // Call
        val packet = SetTempBasalPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCodeAndDuration() {
        // TODO
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCodeAndDurationAndRate() {
        // TODO
    }

    @Test fun handleResponseGivenResponseWhenMessageIsCorrectLengthThenResultTrue() {
        // TODO
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 24
        val responseCode = 0
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)

        // Call        
        val packet = ReadBolusStatePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertEquals(false, result)
        assertEquals(true, packet.failed)
        // assertEquals(byteArrayOf().contentToString(), packet.bolusData.contentToString()) TODO
    }
}
