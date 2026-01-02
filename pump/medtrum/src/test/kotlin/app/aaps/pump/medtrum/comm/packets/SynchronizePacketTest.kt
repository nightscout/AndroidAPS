package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class SynchronizePacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

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
                it.medtrumTimeUtil = medtrumTimeUtil
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
        assertThat(result).asList().containsExactly(opCode.toByte())
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
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(packet.medtrumPump.pumpState.state).isEqualTo(state)
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
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }

    @Test fun handleResponseContainingSyncDataThenDataSaved() {
        // Inputs
        val byteData =
            byteArrayOf(
                47,
                3,
                3,
                1,
                0,
                0,
                32,
                -18,
                13,
                -128,
                5,
                0,
                -128,
                0,
                0,
                6,
                25,
                0,
                14,
                0,
                84,
                -93,
                -83,
                17,
                17,
                64,
                0,
                -104,
                14,
                -8,
                -119,
                -83,
                17,
                -16,
                11,
                90,
                26,
                0,
                14,
                0,
                -69,
                31,
                0,
                0,
                -116,
                14,
                -56
            )

        // Call
        val packet = SynchronizePacket(packetInjector)
        val result = packet.handleResponse(byteData)

        // Expected values
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(packet.medtrumPump.pumpState).isEqualTo(MedtrumPumpState.ACTIVE)
        assertThat(packet.medtrumPump.lastBasalType).isEqualTo(BasalType.ABSOLUTE_TEMP)
        assertThat(packet.medtrumPump.lastBasalRate).isWithin(0.01).of(0.85)
        assertThat(packet.medtrumPump.lastBasalSequence).isEqualTo(25)
        assertThat(packet.medtrumPump.lastBasalPatchId).isEqualTo(14)
        assertThat(packet.medtrumPump.lastBasalStartTime).isEqualTo(1685126612000)
        assertThat(packet.medtrumPump.patchAge).isEqualTo(8123)
        assertThat(packet.medtrumPump.reservoir).isWithin(0.01).of(186.80)
        assertThat(packet.medtrumPump.patchStartTime).isEqualTo(1685120120000)
        assertThat(packet.medtrumPump.batteryVoltage_A).isWithin(0.01).of(5.96875)
        assertThat(packet.medtrumPump.batteryVoltage_B).isWithin(0.01).of(2.8125)
    }
}
