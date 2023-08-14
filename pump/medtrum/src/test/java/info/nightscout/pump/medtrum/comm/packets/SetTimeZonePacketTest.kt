package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetTimeZonePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
            if (it is SetTimeZonePacket) {
                it.dateUtil = dateUtil
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 12
        val time = MedtrumTimeUtil().getCurrentTimePumpSeconds()
        val offsetMinutes = dateUtil.getTimeZoneOffsetMinutes(dateUtil.now())

        // Call
        val packet = SetTimeZonePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expectedByteArray = byteArrayOf(opCode.toByte()) + offsetMinutes.toByteArray(2) + time.toByteArray(4)
        assertEquals(7, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val response = byteArrayOf(7, 12, 4, 0, 0, 0, -78)

        // Call
        val packet = SetTimeZonePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        val expectedOffsetMinutes = 0

        assertTrue(result)
        assertEquals(expectedOffsetMinutes, medtrumPump.pumpTimeZoneOffset)
    }
}
