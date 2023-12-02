package app.aaps.plugins.sync.garmin


import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class DeltaVarEncodedListTest {

    @Test fun empty() {
        val l = DeltaVarEncodedList(100, 2)
        assertArrayEquals(IntArray(0), l.toArray())
    }

    @Test fun add1() {
        val l = DeltaVarEncodedList(100, 2)
        l.add(10, 12)
        assertArrayEquals(intArrayOf(10, 12), l.toArray())
    }

    @Test fun add2() {
        val l = DeltaVarEncodedList(100, 2)
        l.add(10, 16)
        l.add(17, 9)
        assertArrayEquals(intArrayOf(10, 16, 17, 9), l.toArray())
    }

    @Test fun add3() {
        val l = DeltaVarEncodedList(100, 2)
        l.add(10, 16)
        l.add(17, 9)
        l.add(-4, 5)
        assertArrayEquals(intArrayOf(10, 16, 17, 9, -4, 5), l.toArray())
    }

    @Test fun decode() {
        val bytes = ByteBuffer.allocate(6)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putChar(65044.toChar())
        bytes.putChar(33026.toChar())
        bytes.putChar(4355.toChar())
        val l = DeltaVarEncodedList(intArrayOf(-1), bytes)
        assertEquals(4, l.size.toLong())
        assertArrayEquals(intArrayOf(10, 201, 8, -1), l.toArray())
    }

    @Test fun decodeUneven() {
        val bytes = ByteBuffer.allocate(8)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putChar(65044.toChar())
        bytes.putChar(33026.toChar())
        bytes.putChar(59395.toChar())
        bytes.putChar(10.toChar())
        val l = DeltaVarEncodedList(intArrayOf(700), ByteBuffer.wrap(bytes.array(), 0, 7))
        assertEquals(4, l.size.toLong())
        assertArrayEquals(intArrayOf(10, 201, 8, 700), l.toArray())
    }

    @Test fun decodeInt() {
        val bytes = ByteBuffer.allocate(8)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(-2130510316).putInt(714755)
        val l = DeltaVarEncodedList(intArrayOf(700), ByteBuffer.wrap(bytes.array(), 0, 7))
        assertEquals(4, l.size.toLong())
        assertArrayEquals(intArrayOf(10, 201, 8, 700), l.toArray())
    }

    @Test fun decodeInt1() {
        val bytes = ByteBuffer.allocate(3 * 4)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(-2019904035).putInt(335708683).putInt(529409)
        val l = DeltaVarEncodedList(intArrayOf(1483884930, 132), ByteBuffer.wrap(bytes.array(), 0, 11))
        assertEquals(3, l.size.toLong())
        assertArrayEquals(intArrayOf(1483884910, 129, 1483884920, 128, 1483884930, 132), l.toArray())
    }

    @Test fun decodeInt2() {
        val bytes = ByteBuffer.allocate(100)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .putInt(-1761405951)
            .putInt(335977999)
            .putInt(335746050)
            .putInt(336008197)
            .putInt(335680514)
            .putInt(335746053)
            .putInt(-1761405949)
        val l = DeltaVarEncodedList(intArrayOf(1483880370, 127), ByteBuffer.wrap(bytes.array(), 0, 28))
        assertEquals(12, l.size.toLong())
        assertArrayEquals(
            intArrayOf(
                1483879986,
                999,
                1483879984,
                27,
                1483880383,
                37,
                1483880384,
                47,
                1483880382,
                57,
                1483880379,
                67,
                1483880375,
                77,
                1483880376,
                87,
                1483880377,
                97,
                1483880374,
                107,
                1483880372,
                117,
                1483880370,
                127
            ),
            l.toArray()
        )
    }

    @Test fun decodeInt3() {
        val bytes = ByteBuffer.allocate(2 * 4)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(-2020427796).putInt(166411)
        val l = DeltaVarEncodedList(intArrayOf(1483886070, 133), ByteBuffer.wrap(bytes.array(), 0, 7))
        assertEquals(1, l.size.toLong())
        assertArrayEquals(intArrayOf(1483886070, 133), l.toArray())
    }

    @Test fun decodePairs() {
        val bytes = ByteBuffer.allocate(10)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putChar(51220.toChar())
        bytes.putChar(65025.toChar())
        bytes.putChar(514.toChar())
        bytes.putChar(897.toChar())
        bytes.putChar(437.toChar())
        val l = DeltaVarEncodedList(intArrayOf(8, 10), bytes)
        assertEquals(3, l.size.toLong())
        assertArrayEquals(intArrayOf(10, 100, 201, 101, 8, 10), l.toArray())
    }

    @Test fun encoding() {
        val l = DeltaVarEncodedList(100, 2)
        l.add(10, 16)
        l.add(17, 9)
        l.add(-4, 5)
        val dataList = l.encodedData()
        val byteBuffer = ByteBuffer.allocate(dataList.size * 8)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val longBuffer = byteBuffer.asLongBuffer()
        for (i in dataList.indices) {
            longBuffer.put(dataList[i])
        }
        byteBuffer.rewind()
        byteBuffer.limit(l.byteSize)
        val l2 = DeltaVarEncodedList(intArrayOf(-4, 5), byteBuffer)
        assertArrayEquals(intArrayOf(10, 16, 17, 9, -4, 5), l2.toArray())
    }

    @Test fun encoding2() {
        val l = DeltaVarEncodedList(100, 2)
        val values = intArrayOf(
            1511636926, 137, 1511637226, 138, 1511637526, 138, 1511637826, 137, 1511638126, 136,
            1511638426, 135, 1511638726, 134, 1511639026, 132, 1511639326, 130, 1511639626, 128,
            1511639926, 126, 1511640226, 124, 1511640526, 121, 1511640826, 118, 1511641127, 117,
            1511641427, 116, 1511641726, 115, 1511642027, 113, 1511642326, 111, 1511642627, 109,
            1511642927, 107, 1511643227, 107, 1511643527, 107, 1511643827, 106, 1511644127, 105,
            1511644427, 104, 1511644727, 104, 1511645027, 104, 1511645327, 104, 1511645626, 104,
            1511645926, 104, 1511646226, 105, 1511646526, 106, 1511646826, 107, 1511647126, 109,
            1511647426, 108
        )

        for(i in values.indices step 2) {
            l.add(values[i], values[i + 1])
        }
        assertArrayEquals(values, l.toArray())
        val dataList = l.encodedData()
        val byteBuffer = ByteBuffer.allocate(dataList.size * 8)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val longBuffer = byteBuffer.asLongBuffer()
        for (i in dataList.indices) {
            longBuffer.put(dataList[i])
        }
        byteBuffer.rewind()
        byteBuffer.limit(l.byteSize)
        val l2 = DeltaVarEncodedList(intArrayOf(1511647426, 108), byteBuffer)
        assertArrayEquals(values, l2.toArray())
    }
}
