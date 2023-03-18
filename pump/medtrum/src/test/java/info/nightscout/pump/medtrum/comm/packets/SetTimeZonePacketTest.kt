package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetTimeZonePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
            if (it is SetTimeZonePacket) {
                it.dateUtil = dateUtil
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 12
        val time = MedtrumTimeUtil().getCurrentTimePumpSeconds() // TODO: Mock time proper?
        val offsetMins = dateUtil.getTimeZoneOffsetMinutes(dateUtil.now()) // TODO: Mock time proper?

        // Call
        val packet = SetTimeZonePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expectedByteArray = byteArrayOf(opCode.toByte()) + offsetMins.toByteArray(2) + time.toByteArray(4)
        assertEquals(7, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }
}
