package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers the response-packet lookup table: every registered packet is reachable by its msgType and an unknown
 * command falls back to a base [DiaconnG8Packet]. Every packet's init only logs via aapsLogger, so injecting the
 * base fields is enough to build the whole table.
 */
class DiaconnG8ResponseMessageHashTableTest : TestBaseWithProfile() {

    private val packetInjector = MetroMemberInjector {
        if (it is DiaconnG8Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
        }
        true
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

    /** 0xD8 and 0xD9 were both read as an injection block, so a low reservoir report was never read. */
    @Test
    fun eachAlarmReportHasItsOwnType() {
        val table = DiaconnG8ResponseMessageHashTable(packetInjector)

        assertThat(table.findMessage(typeOnTheWire(0xD7))).isInstanceOf(BatteryWarningReportPacket::class.java)
        assertThat(table.findMessage(typeOnTheWire(0xD8))).isInstanceOf(InjectionBlockReportPacket::class.java)
        assertThat(table.findMessage(typeOnTheWire(0xD9))).isInstanceOf(InsulinLackReportPacket::class.java)
    }

    /** The key the service looks up: `DiaconnG8Packet.getCmd` reads the type byte as a signed Int. */
    private fun typeOnTheWire(type: Int): Int = type.toByte().toInt()

    @Test
    fun aSecondPacketWithTheSameTypeIsRefused() {
        val table = DiaconnG8ResponseMessageHashTable(packetInjector)

        assertThrows<IllegalStateException> { table.put(InjectionBlockReportPacket(packetInjector)) }
    }

    @Test
    fun findMessageReturnsBasePacketForUnknownCommand() {
        val table = DiaconnG8ResponseMessageHashTable(packetInjector)

        val unknown = table.findMessage(0x7FFF) // no packet registers this msgType
        assertThat(unknown.friendlyName).isEqualTo("UNKNOWN_PACKET")
    }
}
