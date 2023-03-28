package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SynchronizePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SynchronizePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
            if (it is NotificationPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 3

        // Call
        val packet = SynchronizePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
    }

    @Test fun handleResponseGivenResponseWhenMessageIsCorrectLengthThenResultTrue() {
        // Inputs
        val opCode = 3
        val responseCode = 0
        val state: Byte = 1
        val dataFieldsPresent = 4046
        val syncData = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42)
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + state + dataFieldsPresent.toByteArray(2) + syncData

        // Call        
        val packet = SynchronizePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertEquals(true, result)
        assertEquals(false, packet.failed)
        assertEquals(state, packet.medtrumPump.pumpState.state)
        // TODO: Maybe test cutting behavoir
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 3
        val responseCode = 0
        val state = 1
        val dataFieldsPresent = 4046
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + state.toByteArray(1) + dataFieldsPresent.toByteArray(2)

        // Call
        val packet = SynchronizePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertEquals(false, result)
        assertEquals(true, packet.failed)
    }
}
