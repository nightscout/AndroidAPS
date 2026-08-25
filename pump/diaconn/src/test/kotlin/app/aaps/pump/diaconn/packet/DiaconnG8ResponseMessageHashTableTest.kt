package app.aaps.pump.diaconn.packet

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

/**
 * Covers the response-packet lookup table: every registered packet is reachable by its msgType and an unknown
 * command falls back to a base [DiaconnG8Packet]. Every packet's init only logs via aapsLogger, so injecting the
 * base fields is enough to build the whole table.
 */
class DiaconnG8ResponseMessageHashTableTest : TestBaseWithProfile() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DiaconnG8Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
        }
    }

    @Test
    fun findMessageReturnsTheRegisteredPacketForItsMsgType() {
        val table = DiaconnG8ResponseMessageHashTable(packetInjector)

        assertThat(table.messages).isNotEmpty()
        val big = BigLogInquireResponsePacket(packetInjector)
        assertThat(table.findMessage(big.msgType.toInt())).isInstanceOf(BigLogInquireResponsePacket::class.java)
        val tempBasal = TempBasalInquireResponsePacket(packetInjector)
        assertThat(table.findMessage(tempBasal.msgType.toInt())).isInstanceOf(TempBasalInquireResponsePacket::class.java)
    }

    @Test
    fun findMessageReturnsBasePacketForUnknownCommand() {
        val table = DiaconnG8ResponseMessageHashTable(packetInjector)

        val unknown = table.findMessage(0x7FFF) // no packet registers this msgType
        assertThat(unknown.friendlyName).isEqualTo("UNKNOWN_PACKET")
    }
}
