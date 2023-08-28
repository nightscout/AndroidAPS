package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SynchronizePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

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

    @Test fun handleResponseContainingSyncDataThenDataSaved() {
        // Inputs
        val data = byteArrayOf(47, 3, 3, 1, 0, 0, 32, -18, 13, -128, 5, 0, -128, 0, 0, 6, 25, 0, 14, 0, 84, -93, -83, 17, 17, 64, 0, -104, 14, -8, -119, -83, 17, -16, 11, 90, 26, 0, 14, 0, -69, 31, 0, 0, -116, 14, -56)

        // Call
        val packet = SynchronizePacket(packetInjector)
        val result = packet.handleResponse(data)

        // Expected values
        assertEquals(true, result)
        assertEquals(false, packet.failed)
        assertEquals(MedtrumPumpState.ACTIVE, packet.medtrumPump.pumpState)
        assertEquals(BasalType.ABSOLUTE_TEMP, packet.medtrumPump.lastBasalType)
        assertEquals(0.85, packet.medtrumPump.lastBasalRate, 0.01)
        assertEquals(25, packet.medtrumPump.lastBasalSequence)
        assertEquals(14, packet.medtrumPump.lastBasalPatchId)
        assertEquals(1685126612000, packet.medtrumPump.lastBasalStartTime)
        assertEquals(8123, packet.medtrumPump.patchAge)
        assertEquals(186.80, packet.medtrumPump.reservoir, 0.01)
        assertEquals(1685120120000, packet.medtrumPump.patchStartTime)
        assertEquals(5.96875, packet.medtrumPump.batteryVoltage_A, 0.01)
        assertEquals(2.8125, packet.medtrumPump.batteryVoltage_B, 0.01)
    }
}
