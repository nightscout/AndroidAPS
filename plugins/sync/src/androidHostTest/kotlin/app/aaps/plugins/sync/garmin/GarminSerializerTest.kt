package app.aaps.plugins.sync.garmin


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class GarminSerializerTest {

    @Test fun testSerializeDeserializeString() {
        val o = "Hello, world!"
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(
                -85, -51, -85, -51, 0, 0, 0, 16, 0, 14, 72, 101, 108, 108, 111, 44, 32, 119, 111,
                114, 108, 100, 33, 0, -38, 122, -38, 122, 0, 0, 0, 5, 3, 0,0, 0, 0),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
    }

    @Test fun testSerializeDeserializeInteger() {
        val o = 3
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(-38, 122, -38, 122, 0, 0, 0, 5, 1, 0, 0, 0, 3),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
    }

    @Test fun tesSerializeDeserializeArray() {
        val o = listOf("a", "b", true, 3, 3.4F, listOf(5L, 9), 42)
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(
                -85, -51, -85, -51, 0, 0, 0, 8, 0, 2, 97, 0, 0, 2, 98, 0, -38, 122, -38, 122, 0, 0,
                0, 55, 5, 0, 0, 0, 7, 3, 0, 0, 0, 0, 3, 0, 0, 0, 4, 9, 1, 1, 0, 0, 0, 3, 2, 64, 89,
                -103, -102, 5, 0, 0, 0, 2, 1, 0, 0, 0, 42, 14, 0, 0, 0, 0, 0, 0, 0, 5, 14, 0, 0, 0,
                0, 0, 0, 0, 9),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
    }

    @Test
    fun testSerializeDeserializeMap() {
        val o = mapOf("a" to "abc", "c" to 3, "d" to listOf(4, 9, "abc"), true to null)
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(
                -85, -51, -85, -51, 0, 0, 0, 18, 0, 2, 97, 0, 0, 4, 97, 98, 99, 0, 0, 2, 99, 0, 0,
                2, 100, 0, -38, 122, -38, 122, 0, 0, 0, 53, 11, 0, 0, 0, 4, 3, 0, 0, 0, 0, 3, 0, 0,
                0, 4, 3, 0, 0, 0, 10, 1, 0, 0, 0, 3, 3, 0, 0, 0, 14, 5, 0, 0, 0, 3, 9, 1, 0, 1, 0, 0,
                0, 4, 1, 0, 0, 0, 9, 3, 0, 0, 0, 4),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
    }

    @Test fun testSerializeDeserializeNull() {
        val o = null
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(-38, 122, -38, 122, 0, 0, 0, 1, 0),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
        assertEquals(o, GarminSerializer.deserialize(data))
    }

    @Test fun testSerializeDeserializeAllPrimitiveTypes() {
        val o = listOf(1, 1.2F, 1.3, "A", true, 2L, 'X', null)
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(
                -85, -51, -85, -51, 0, 0, 0, 4, 0, 2, 65, 0, -38, 122, -38, 122, 0, 0, 0, 46, 5, 0,
                0, 0, 8, 1, 0, 0, 0, 1, 2, 63, -103, -103, -102, 15, 63, -12, -52, -52, -52, -52,
                -52, -51, 3, 0, 0, 0, 0, 9, 1, 14, 0, 0, 0, 0, 0, 0, 0, 2, 19, 0, 0, 0, 88, 0),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
        assertEquals(o,  GarminSerializer.deserialize(data))
    }

    @Test fun testSerializeDeserializeMapNested() {
        val o = mapOf("a" to "abc", "c" to 3, "d" to listOf(4, 9, "abc"))
        val data = GarminSerializer.serialize(o)
        assertContentEquals(
            byteArrayOf(
                -85, -51, -85, -51, 0, 0, 0, 18, 0, 2, 97, 0, 0, 4, 97, 98, 99, 0, 0, 2, 99, 0, 0,
                2, 100, 0, -38, 122, -38, 122, 0, 0, 0, 50, 11, 0, 0, 0, 3, 3, 0, 0, 0, 0, 3, 0, 0,
                0, 4, 3, 0, 0, 0, 10, 1, 0, 0, 0, 3, 3, 0, 0, 0, 14, 5, 0, 0, 0, 3, 1, 0, 0, 0, 4,
                1, 0, 0, 0, 9, 3, 0, 0, 0, 4),
            data)
        assertEquals(o, GarminSerializer.deserialize(data))
    }
}