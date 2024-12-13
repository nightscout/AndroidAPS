package app.aaps.pump.medtrum.comm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WriteCommandPacketsTest {

    @Test
    fun given14LongCommandExpectOnePacket() {
        val input = byteArrayOf(5, 2, 0, 0, 0, 0, -21, 57, -122, -56)
        val expected = byteArrayOf(14, 5, 0, 0, 2, 0, 0, 0, 0, -21, 57, -122, -56, -93, 0)
        val sequence = 0
        val cmdPackets = WriteCommandPackets(input, sequence)
        val output = cmdPackets.getNextPacket()

        assertThat(output.contentToString()).isEqualTo(expected.contentToString())
    }

    @Test
    fun given41LongCommandExpectThreePackets() {
        val input = byteArrayOf(18, 0, 12, 0, 3, 0, 1, 30, 32, 3, 16, 14, 0, 0, 1, 7, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -20, 36, 2, 100, -123, 2)
        val expected1 = byteArrayOf(41, 18, 0, 1, 0, 12, 0, 3, 0, 1, 30, 32, 3, 16, 14, 0, 0, 1, 7, -121)
        val expected2 = byteArrayOf(41, 18, 0, 2, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -3)
        val expected3 = byteArrayOf(41, 18, 0, 3, -20, 36, 2, 100, -123, 2, -125, -89)

        val sequence = 0
        val cmdPackets = WriteCommandPackets(input, sequence)
        val output1 = cmdPackets.getNextPacket()
        val output2 = cmdPackets.getNextPacket()
        val output3 = cmdPackets.getNextPacket()
        val output4 = cmdPackets.getNextPacket()


        assertThat(output1.contentToString()).isEqualTo(expected1.contentToString())
        assertThat(output2.contentToString()).isEqualTo(expected2.contentToString())
        assertThat(output3.contentToString()).isEqualTo(expected3.contentToString())
        assertThat(output4).isNull()
        assertThat(cmdPackets.allPacketsConsumed()).isTrue()
    }
}
