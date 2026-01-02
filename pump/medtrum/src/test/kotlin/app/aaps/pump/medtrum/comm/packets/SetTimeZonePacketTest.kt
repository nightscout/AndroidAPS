package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class SetTimeZonePacketTest : MedtrumTestBase() {

    @Mock lateinit var medtrumTimeUtil: MedtrumTimeUtil

    /** Test packet specific behavior */

    @BeforeEach
    fun mock() {
        whenever(medtrumTimeUtil.getCurrentTimePumpSeconds()).thenReturn(1234567890)
    }

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
            if (it is SetTimeZonePacket) {
                it.dateUtil = dateUtil
                it.medtrumPump = medtrumPump
                it.medtrumTimeUtil = medtrumTimeUtil
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 12
        val time = medtrumTimeUtil.getCurrentTimePumpSeconds()
        val offsetMinutes = dateUtil.getTimeZoneOffsetMinutes(dateUtil.now())

        // Call
        val packet = SetTimeZonePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(opCode.toByte()) + offsetMinutes.toByteArray(2) + time.toByteArray(4)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val response = byteArrayOf(7, 12, 4, 0, 0, 0, -78)
        val offsetMinutes = dateUtil.getTimeZoneOffsetMinutes(dateUtil.now())

        // Call
        val packet = SetTimeZonePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isTrue()
        assertThat(medtrumPump.pumpTimeZoneOffset).isEqualTo(offsetMinutes)
    }
}
