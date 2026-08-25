package app.aaps.pump.diaconn.packet

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

/**
 * Covers the setting-response lookup table: registered packets are reachable by their msgType and an unknown
 * command falls back to a base [DiaconnG8Packet].
 */
class DiaconnG8SettingResponseMessageHashTableTest : TestBaseWithProfile() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DiaconnG8Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
        }
    }

    @Test
    fun findMessageReturnsTheRegisteredSettingPacketForItsMsgType() {
        val table = DiaconnG8SettingResponseMessageHashTable(packetInjector)

        assertThat(table.messages).isNotEmpty()
        val confirm = AppConfirmSettingResponsePacket(packetInjector)
        assertThat(table.findMessage(confirm.msgType.toInt())).isInstanceOf(AppConfirmSettingResponsePacket::class.java)
        val basalSetting = BasalSettingResponsePacket(packetInjector)
        assertThat(table.findMessage(basalSetting.msgType.toInt())).isInstanceOf(BasalSettingResponsePacket::class.java)
    }

    @Test
    fun findMessageReturnsBasePacketForUnknownCommand() {
        val table = DiaconnG8SettingResponseMessageHashTable(packetInjector)

        val unknown = table.findMessage(0x7FFF)
        assertThat(unknown.friendlyName).isEqualTo("UNKNOWN_PACKET")
    }
}
