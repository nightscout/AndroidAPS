package app.aaps.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test

class ReadBolusStatePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val opCode = 34
        val responseCode = 0
        val bolusData = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + bolusData

        // Call
        val packet = ReadBolusStatePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(packet.bolusData).isEqualTo(bolusData)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 34
        val responseCode = 0
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)

        // Call
        val packet = ReadBolusStatePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
        assertThat(packet.bolusData).isEmpty()
    }
}
