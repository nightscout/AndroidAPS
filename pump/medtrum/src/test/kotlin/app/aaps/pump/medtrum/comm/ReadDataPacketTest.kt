package app.aaps.pump.medtrum.comm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ReadDataPacketTest {

    @Test
    fun givenCorrectBytesExpectPacketNotFailed() {
        // arrange
        val chunk1 = byteArrayOf(51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -108, -62, 1, 0, 22, 0, 1, 75)
        val chunk2 = byteArrayOf(51, 99, 10, 2, 0, 0, 0, -80, -116, 84, 18, 10, 0, 10, 0, 0, 0, 0, 0, -10)
        val chunk3 = byteArrayOf(51, 99, 10, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -61, -59, -120, 5)
        val chunk4 = byteArrayOf(51, 99, 10, 4, 19, -82, -80)

        // act
        val packet = ReadDataPacket(chunk1)
        packet.addData(chunk2)
        packet.addData(chunk3)
        packet.addData(chunk4)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.getData()).isEqualTo(
            byteArrayOf(
                51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -108, -62, 1, 0, 22, 0, 1, 0, 0, 0, -80,
                -116, 84, 18, 10, 0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -61, -59, -120, 19, -82
            )
        )
        assertThat(packet.failed()).isFalse()
    }

    @Test
    fun givenIncorrectCRCInFirstChunkExpectPacketFailed() {
        // arrange
        val chunk1 = byteArrayOf(51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -1, -62, -1, -1, 22, 0, 1, 75)
        val chunk2 = byteArrayOf(51, 99, 10, 2, 0, 0, 0, -80, -116, 84, 18, 10, 0, 10, 0, 0, 0, 0, 0, -10)
        val chunk3 = byteArrayOf(51, 99, 10, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -61, -59, -120, 5)
        val chunk4 = byteArrayOf(51, 99, 10, 4, 19, -82, -80)

        // act
        val packet = ReadDataPacket(chunk1)
        packet.addData(chunk2)
        packet.addData(chunk3)
        packet.addData(chunk4)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.failed()).isTrue()
    }

    @Test
    fun givenIncorrectCRCInSecondChunkExpectPacketFailed() {
        // arrange
        val chunk1 = byteArrayOf(51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -108, -62, 1, 0, 22, 0, 1, 75)
        val chunk2 = byteArrayOf(51, 99, 10, 2, 0, 0, 0, -80, -1, 84, 18, 10, 0, 10, -1, -1, 0, 0, 0, -10)
        val chunk3 = byteArrayOf(51, 99, 10, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -61, -59, -120, 5)
        val chunk4 = byteArrayOf(51, 99, 10, 4, 19, -82, -80)

        // act
        val packet = ReadDataPacket(chunk1)
        packet.addData(chunk2)
        packet.addData(chunk3)
        packet.addData(chunk4)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.failed()).isTrue()
    }

    @Test
    fun givenIncorrectCRCInThirdChunkExpectPacketFailed() {
        // arrange
        val chunk1 = byteArrayOf(51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -108, -62, 1, 0, 22, 0, 1, 75)
        val chunk2 = byteArrayOf(51, 99, 10, 2, 0, 0, 0, -80, -116, 84, 18, 10, 0, 10, 0, 0, 0, 0, 0, -10)
        val chunk3 = byteArrayOf(51, 99, 10, 3, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -61, -59, -120, 5)
        val chunk4 = byteArrayOf(51, 99, 10, 4, 19, -82, -80)

        // act
        val packet = ReadDataPacket(chunk1)
        packet.addData(chunk2)
        packet.addData(chunk3)
        packet.addData(chunk4)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.failed()).isTrue()
    }

    @Test
    fun givenIncorrectCRCInLastChunkExpectPacketFailed() {
        // arrange
        val chunk1 = byteArrayOf(51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -108, -62, 1, 0, 22, 0, 1, 75)
        val chunk2 = byteArrayOf(51, 99, 10, 2, 0, 0, 0, -80, -116, 84, 18, 10, 0, 10, 0, 0, 0, 0, 0, -10)
        val chunk3 = byteArrayOf(51, 99, 10, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -61, -59, -120, 5)
        val chunk4 = byteArrayOf(51, 99, 10, -1, -1, -82, -80)

        // act
        val packet = ReadDataPacket(chunk1)
        packet.addData(chunk2)
        packet.addData(chunk3)
        packet.addData(chunk4)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.failed()).isTrue()
    }

    @Test
    fun givenIncorrectSequenceExpectPacketFailed() {
        // arrange
        val chunk1 = byteArrayOf(51, 99, 10, 1, 0, 0, -86, 44, 1, -1, -85, 21, -108, -62, 1, 0, 22, 0, 1, 75)
        val chunk2 = byteArrayOf(51, 99, 10, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -61, -59, -120, 5)
        val chunk3 = byteArrayOf(51, 99, 10, 2, 0, 0, 0, -80, -116, 84, 18, 10, 0, 10, 0, 0, 0, 0, 0, -10)
        val chunk4 = byteArrayOf(51, 99, 10, 4, 19, -82, -80)

        // act
        val packet = ReadDataPacket(chunk1)
        packet.addData(chunk2)
        packet.addData(chunk3)
        packet.addData(chunk4)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.failed()).isTrue()
    }

    @Test
    fun givenCorrectBytesOneChunkExpectPacketNotFailed() {
        // arrange
        val chunk1 = byteArrayOf(14, 5, 0, 0, 0, 0, 2, 80, 1, 74, 64, 4, 0, -16, 0)

        // act
        val packet = ReadDataPacket(chunk1)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.getData()).isEqualTo(byteArrayOf(14, 5, 0, 0, 0, 0, 2, 80, 1, 74, 64, 4, 0, -16))
        assertThat(packet.failed()).isFalse()
    }

    @Test
    fun givenIncorrectBytesOneChunkExpectPacketFailed() {
        // arrange
        val chunk1 = byteArrayOf(14, 5, 0, -1, -1, -1, 2, 80, 1, 74, 64, 4, 0, -16, 0)

        // act
        val packet = ReadDataPacket(chunk1)

        // assert
        assertThat(packet.allDataReceived()).isTrue()
        assertThat(packet.failed()).isTrue()
    }
}
