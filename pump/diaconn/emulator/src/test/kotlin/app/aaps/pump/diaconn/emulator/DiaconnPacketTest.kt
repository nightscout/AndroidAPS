package app.aaps.pump.diaconn.emulator

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The framing and the log records, checked against bytes of a real G8 (DIACONN-64004, fw 2.50, 2026-11-11). */
class DiaconnPacketTest {

    @Test
    fun aTimeInquiryIsFramedAsTheDriverFramesIt() {
        assertThat(hex(DiaconnPacket.build(0x4F, 0, ByteArray(0)))).isEqualTo("ef 4f 00 00 ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff b7")
    }

    @Test
    fun theSerialNumberAnswerIsTheOneOfTheRealPump() {
        val answer = DiaconnPumpEmulator(DiaconnPumpState().apply { serialNumber = REAL_SERIAL }).receive(DiaconnPacket.build(0x6E, 0x10, ByteArray(0))).answer

        assertThat(hex(checkNotNull(answer))).isEqualTo("ef ae 10 00 10 31 31 14 06 09 01 04 fa 02 32 ff ff ff ff a0")
    }

    @Test
    fun theCrcOfTheRealMainInfoAnswersIsRight() {
        for (answer in listOf(REAL_MAIN_INFO_CLOCK_NEVER_SET, REAL_MAIN_INFO_CLOCK_SET).map { bytes(it) }) {
            assertThat(answer.size).isEqualTo(DiaconnPacket.SIZE_BIG)
            assertThat(DiaconnPacket.crc(answer, answer.size - 1)).isEqualTo(answer.last())
        }
    }

    @Test
    fun aChangedByteChangesTheCrc() {
        val answer = bytes(REAL_MAIN_INFO_CLOCK_SET)
        answer[100] = (answer[100] + 1).toByte()

        assertThat(DiaconnPacket.crc(answer, answer.size - 1)).isNotEqualTo(answer.last())
    }

    @Test
    fun aTempBasalStopRecordIsTheOneOfTheRealPump() {
        // Record 4054: 2026-11-11 19:23:00, 0.80 U/h, completed, TB_DTTM 946652400.
        val record = DiaconnLog.record(0x6af4c094, DiaconnLog.TB_STOP) { u16(1080).u8(0).u32(946652400) }

        assertThat(hex(record)).isEqualTo("94 c0 f4 6a 93 38 04 00 f0 c4 6c 38")
    }

    @Test
    fun aSuspendRecordIsTheOneOfTheRealPump() {
        // 2026-11-11 18:55:12, battery 85, pattern 1.
        val record = DiaconnLog.record(0x6af4ba10, DiaconnLog.SUSPEND) { u8(85).u8(1) }

        assertThat(hex(record)).isEqualTo("10 ba f4 6a 43 55 01 ff ff ff ff ff")
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02x".format(it) }

    private fun bytes(hex: String): ByteArray = hex.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private companion object {

        /** The serial of the real G8 whose answers these tests copy. */
        const val REAL_SERIAL = 64004

        // Copied from BigMainInfoInquireResponsePacketTest of the driver.
        const val REAL_MAIN_INFO_CLOCK_NEVER_SET =
            "ed b3 13 00 10 66 3e 57 01 02 02 02 02 02 02 19 03 13 00 41 15 31 31 14 06 09 01 04 fa 02 32 b1 0f 00 05 02 08 b4 c3 ee 00 02 00 28 " +
                "00 00 00 00 00 00 00 00 02 00 00 00 00 05 02 00 00 00 00 05 02 00 00 01 00 2c 01 00 00 02 00 00 00 00 00 00 01 00 00 00 00 00 02 4a " +
                "df cb 69 14 00 02 cd d8 cb 69 64 00 00 00 00 00 00 00 00 00 00 0a 00 00 0f 00 00 01 00 00 00 00 00 28 00 28 00 28 00 28 00 28 00 28 " +
                "00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 28 00 ff ff ff ff ff ff ff " +
                "ff ff ff ff ff 38"
        const val REAL_MAIN_INFO_CLOCK_SET =
            "ed b3 38 00 10 66 3e 56 01 02 02 02 02 02 02 1a 0b 0b 12 16 32 31 31 14 06 09 01 04 fa 02 32 cc 0f 00 05 02 08 b4 c3 80 03 02 12 78 " +
                "00 00 00 00 00 00 00 00 02 00 00 00 00 05 02 00 00 00 00 05 02 00 00 01 00 2c 01 00 00 02 00 00 00 00 00 00 01 00 00 00 00 00 02 4a " +
                "df cb 69 14 00 02 cd d8 cb 69 64 00 00 00 00 00 00 00 00 00 00 0a 00 00 0f 00 00 01 12 00 00 00 00 3c 00 3c 00 3c 00 3c 00 3c 00 3c " +
                "00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 78 00 5a 00 5a 00 5a 00 5a 00 ff ff ff ff ff ff ff " +
                "ff ff ff ff ff a8"
    }
}
