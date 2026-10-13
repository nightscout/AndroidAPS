package app.aaps.pump.diaconn.packet

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiaconnG8PacketTest : TestBaseWithProfile() {

    @Test
    fun getCRCShouldCalculateCorrectly() {
        // Given
        val data = byteArrayOf(
            0xef.toByte(), 0x6E.toByte(), 0x01.toByte(), 0x00.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte()
        )

        // When
        val crc = DiaconnG8Packet.getCRC(data, 19)

        // Then - CRC should be calculated consistently
        assertThat(crc).isNotEqualTo(0)
        // Verify it's reproducible
        assertThat(DiaconnG8Packet.getCRC(data, 19)).isEqualTo(crc)
    }

    @Test
    fun defectShouldDetectWrongSOP() {
        // Given
        val data = ByteArray(20)
        data[0] = 0x00 // Wrong SOP (should be 0xef)

        // When
        val result = DiaconnG8Packet.defect(data)

        // Then
        assertThat(result).isEqualTo(98) // Start code check error
    }

    @Test
    fun defectShouldDetectWrongLength() {
        // Given
        val data = ByteArray(15) // Wrong length
        data[0] = 0xef.toByte()

        // When
        val result = DiaconnG8Packet.defect(data)

        // Then
        assertThat(result).isEqualTo(97) // Length check error
    }

    @Test
    fun defectShouldDetectWrongCRC() {
        // Given
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // Correct SOP
        data[19] = 0x00 // Wrong CRC

        // When
        val result = DiaconnG8Packet.defect(data)

        // Then
        assertThat(result).isEqualTo(99) // CRC check error
    }

    @Test
    fun defectShouldPassValidPacket() {
        // Given
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x6E.toByte()
        for (i in 2 until 19) {
            data[i] = 0xff.toByte()
        }
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        // When
        val result = DiaconnG8Packet.defect(data)

        // Then
        assertThat(result).isEqualTo(0) // No error
    }

    @Test
    fun defectShouldHandleBigPackets() {
        // Given
        val data = ByteArray(182)
        data[0] = 0xed.toByte() // SOP_BIG
        for (i in 1 until 181) {
            data[i] = 0xff.toByte()
        }
        data[181] = DiaconnG8Packet.getCRC(data, 181)

        // When
        val result = DiaconnG8Packet.defect(data)

        // Then
        assertThat(result).isEqualTo(0) // No error
    }

    @Test
    fun toHexShouldFormatCorrectly() {
        // Given
        val data = byteArrayOf(0xef.toByte(), 0x6E.toByte(), 0x01.toByte())

        // When
        val hex = DiaconnG8Packet.toHex(data)

        // Then
        assertThat(hex).isEqualTo("ef 6e 01 ")
    }

    @Test
    fun toNarrowHexShouldFormatCorrectly() {
        // Given
        val data = byteArrayOf(0xef.toByte(), 0x6E.toByte(), 0x01.toByte())

        // When
        val hex = DiaconnG8Packet.toNarrowHex(data)

        // Then
        assertThat(hex).isEqualTo("ef6e01")
    }

    @Test
    fun getByteToIntShouldHandleUnsignedValues() {
        // Given
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[4] = 0xFF.toByte() // 255 unsigned, -1 signed

        val buffer = DiaconnG8Packet.prefixDecode(data)

        // When
        val value = DiaconnG8Packet.getByteToInt(buffer)

        // Then
        assertThat(value).isEqualTo(255) // Should be unsigned
    }

    @Test
    fun getShortToIntShouldHandleLittleEndian() {
        // Given
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[4] = 0x39.toByte() // Low byte
        data[5] = 0x30.toByte() // High byte
        // Together: 0x3039 = 12345 in little endian

        val buffer = DiaconnG8Packet.prefixDecode(data)

        // When
        val value = DiaconnG8Packet.getShortToInt(buffer)

        // Then
        assertThat(value).isEqualTo(12345)
    }

    @Test
    fun getIntToIntShouldHandleLittleEndian() {
        // Given
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[4] = 0x15.toByte() // Byte 0
        data[5] = 0xCD.toByte() // Byte 1
        data[6] = 0x5B.toByte() // Byte 2
        data[7] = 0x07.toByte() // Byte 3
        // Together: 0x075BCD15 = 123456789 in little endian

        val buffer = DiaconnG8Packet.prefixDecode(data)

        // When
        val value = DiaconnG8Packet.getIntToInt(buffer)

        // Then
        assertThat(value).isEqualTo(123456789)
    }

    @Test
    fun prefixDecodeShouldPositionBufferCorrectly() {
        // Given
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x6E.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = 0x42.toByte() // First data byte

        // When
        val buffer = DiaconnG8Packet.prefixDecode(data)

        // Then
        assertThat(buffer.position()).isEqualTo(4) // Should skip header
        assertThat(buffer.get()).isEqualTo(0x42.toByte()) // Should read first data byte
    }

    @Test
    fun constantsShouldBeCorrect() {
        // Verify packet constants
        assertThat(DiaconnG8Packet.MSG_LEN).isEqualTo(20)
        assertThat(DiaconnG8Packet.MSG_LEN_BIG).isEqualTo(182)
        assertThat(DiaconnG8Packet.SOP).isEqualTo(0xef.toByte())
        assertThat(DiaconnG8Packet.SOP_BIG).isEqualTo(0xed.toByte())
        assertThat(DiaconnG8Packet.MSG_TYPE_LOC).isEqualTo(1)
        assertThat(DiaconnG8Packet.MSG_SEQ_LOC).isEqualTo(2)
        assertThat(DiaconnG8Packet.BT_MSG_DATA_LOC).isEqualTo(4.toByte())
        assertThat(DiaconnG8Packet.MSG_PAD).isEqualTo(0xff.toByte())
        assertThat(DiaconnG8Packet.MSG_CON_END).isEqualTo(0x00.toByte())
        assertThat(DiaconnG8Packet.MSG_CON_CONTINUE).isEqualTo(0x01.toByte())
    }
}
