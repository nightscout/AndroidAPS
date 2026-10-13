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

class SetTimePacketTest : MedtrumTestBase() {

    @Mock lateinit var medtrumTimeUtil: MedtrumTimeUtil

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
            if (it is SetTimePacket) {
                it.medtrumTimeUtil = medtrumTimeUtil
            }
        }
    }

    @BeforeEach
    fun mock() {
        whenever(medtrumTimeUtil.getCurrentTimePumpSeconds()).thenReturn(1234567890)
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 10
        val time = medtrumTimeUtil.getCurrentTimePumpSeconds()

        // Call
        val packet = SetTimePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(opCode.toByte()) + 2.toByte() + time.toByteArray(4)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }
}
