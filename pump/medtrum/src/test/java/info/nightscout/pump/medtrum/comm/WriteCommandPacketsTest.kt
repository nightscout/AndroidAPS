package info.nightscout.pump.medtrum.comm

import org.junit.jupiter.api.Test
import org.junit.Assert.*

class WriteCommandPacketsTest {

    @Test
    fun Given14LongCommandExpectOnePacket() {
        val input = byteArrayOf(5, 2, 0, 0, 0, 0, -21, 57, -122, -56)
        val expect = byteArrayOf(14, 5, 0, 0, 2, 0, 0, 0, 0, -21, 57, -122, -56, -93, 0)
        val cmdPackets = WriteCommandPackets(input)
        val output = cmdPackets.getNextPacket()

        assertEquals(expect.contentToString(), output.contentToString())
    }

    @Test
    fun Given41LongCommandExpectThreePackets() {
        val input = byteArrayOf(18, 0, 12, 0, 3, 0, 1, 30, 32, 3, 16, 14, 0, 0, 1, 7, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -20, 36, 2, 100, -123, 2)
        val expect1 = byteArrayOf(41, 18, 0, 1, 0, 12, 0, 3, 0, 1, 30, 32, 3, 16, 14, 0, 0, 1, 7, -121)
        val expect2 = byteArrayOf(41, 18, 0, 2, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -3)
        val expect3 = byteArrayOf(41, 18, 0, 3, -20, 36, 2, 100, -123, 2, -125, -89)

        val cmdPackets = WriteCommandPackets(input)
        val output1 = cmdPackets.getNextPacket()
        val output2 = cmdPackets.getNextPacket()
        val output3 = cmdPackets.getNextPacket()
        val output4 = cmdPackets.getNextPacket()


        assertEquals(expect1.contentToString(), output1.contentToString())
        assertEquals(expect2.contentToString(), output2.contentToString())
        assertEquals(expect3.contentToString(), output3.contentToString())
        assertNull(output4)
        assertEquals(true, cmdPackets.allPacketsConsumed())

    }

    @Test
    fun Given2CommandsExpectWriteIndexInHeaderIncrease() {
        val input1 = byteArrayOf(66)
        val input2 = byteArrayOf(99)

        val expect1 = byteArrayOf(5, 66, 0, 0, -25, 0)
        val expect2 = byteArrayOf(5, 99, 1, 0, 64, 0)

        val cmdPackets = WriteCommandPackets(input1)

        val output1 = cmdPackets.getNextPacket()

        cmdPackets.setData(input2)

        val output2 = cmdPackets.getNextPacket()

        assertEquals(expect1.contentToString(), output1.contentToString())
        assertEquals(expect2.contentToString(), output2.contentToString())
    }

    @Test
    fun GivenWriteIndexOverflowExpectWriteIndex1() {
        val input1 = byteArrayOf(55)
        val input2 = byteArrayOf(66)
        val input3 = byteArrayOf(99)

        val expect1 = byteArrayOf(5, 55, -2, 0, -19, 0)
        val expect2 = byteArrayOf(5, 66, -1, 0, 86, 0)
        val expect3 = byteArrayOf(5, 99, 1, 0, 64, 0)

        val cmdPackets = WriteCommandPackets(byteArrayOf(0.toByte()))

        // All this stuff to set the private field ^^
        val writeCommandIndex = WriteCommandPackets::class.java.getDeclaredField("writeCommandIndex")
        writeCommandIndex.isAccessible = true
        writeCommandIndex.setInt(cmdPackets, 254)

        cmdPackets.setData(input1)
        val output1 = cmdPackets.getNextPacket()

        cmdPackets.setData(input2)
        val output2 = cmdPackets.getNextPacket()

        cmdPackets.setData(input3)
        val output3 = cmdPackets.getNextPacket()

        assertEquals(expect1.contentToString(), output1.contentToString())
        assertEquals(expect2.contentToString(), output2.contentToString())
        assertEquals(expect3.contentToString(), output3.contentToString())
    }
}
