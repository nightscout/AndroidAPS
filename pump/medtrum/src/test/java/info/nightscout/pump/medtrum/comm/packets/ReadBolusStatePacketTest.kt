package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Assertions
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
        Assertions.assertTrue(result)
        Assertions.assertFalse(packet.failed)
        Assertions.assertEquals(bolusData.contentToString(), packet.bolusData.contentToString())
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
        Assertions.assertEquals(false, result)
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals(byteArrayOf().contentToString(), packet.bolusData.contentToString())
    }
}
