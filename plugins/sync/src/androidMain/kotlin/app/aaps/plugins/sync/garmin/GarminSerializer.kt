package app.aaps.plugins.sync.garmin

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Queue

/**
 * Serialize and Deserialize objects in Garmin format.
 *
 * Format is as follows:
 * <STRS_MARKER><STRS_LEN><STRINGS><OBJS_MARKER><OBJS_LENGTH><OBJ><OBJ>...
 *
 * Serialized data starts with an optional string block. The string block is preceded with the STRS_MARKER,
 * followed by the total length of the reminder (4 bytes). Then foreach string, the string length
 * (2 bytes), followed by the string bytes, followed by a \0 byte.
 *
 * Objects are stored starting with OBJS_MARKER, followed by the total length (4 bytes), followed
 * by a flat list of objects. Each object starts with its type (1 byte), followed by the data
 * for numbers in Boolean. Strings a represented by an index into the string block. Arrays only have
 * the length, the actual objects will be in the list of objects. Similarly, maps only have the
 * length and the entries are represented by 2 objects (key + val) in the list of objects.
 */
object GarminSerializer {
    private const val NULL = 0
    private const val INT = 1
    private const val FLOAT = 2
    private const val STRING = 3
    private const val ARRAY = 5
    private const val BOOLEAN = 9
    private const val MAP = 11
    private const val LONG = 14
    private const val DOUBLE = 15
    private const val CHAR = 19

    private const val STRINGS_MARKER = -1412584499
    private const val OBJECTS_MARKER = -629482886
    // ArrayDeque doesn't like null so we use this instead.
    private val NULL_MARKER = object {}

    private interface Container {
        fun read(buf: ByteBuffer, strings: Map<Int, String>, container: Queue<Container>)
    }

    private class ListContainer(
        val size: Int,
        val list: MutableList<Any?>
    ) : Container {

        override fun read(buf: ByteBuffer, strings: Map<Int, String>, container: Queue<Container>) {
            for (i in 0 until size) {
                list.add(readObject(buf, strings, container))
            }
        }
    }

    private class MapContainer(
        val size: Int,
        val map: MutableMap<Any, Any?>
    ) : Container {

        override fun read(buf: ByteBuffer, strings: Map<Int, String>, container: Queue<Container>) {
            for (i in 0 until size) {
                val k = readObject(buf, strings, container)
                val v = readObject(buf, strings, container)
                map[k!!] = v
            }
        }
    }


    fun serialize(obj: Any?): ByteArray {
        val strsOut = ByteArrayOutputStream()
        val strsDataOut = DataOutputStream(strsOut)
        val objsOut = ByteArrayOutputStream()
        val strings = mutableMapOf<String, Int>()
        val q = ArrayDeque<Any?>()

        q.add(obj ?: NULL_MARKER)
        while (!q.isEmpty()) {
            serialize(q.poll(), strsDataOut, DataOutputStream(objsOut), strings, q)
        }

        var bufLen = 8 + objsOut.size()
        if (strsOut.size() > 0) {
            bufLen += 8 + strsOut.size()
        }

        val buf = ByteBuffer.allocate(bufLen)
        if (strsOut.size() > 0) {
            buf.putInt(STRINGS_MARKER)
            buf.putInt(strsOut.size())
            buf.put(strsOut.toByteArray(), 0, strsOut.size())
        }
        buf.putInt(OBJECTS_MARKER)
        buf.putInt(objsOut.size())
        buf.put(objsOut.toByteArray(), 0, objsOut.size())
        return buf.array()
    }

    private fun serialize(
        obj: Any?,
        strOut: DataOutputStream,
        objOut: DataOutputStream,
        strings: MutableMap<String, Int>,
        q: Queue<Any?>
    ) {
        when (obj) {
            NULL_MARKER -> objOut.writeByte(NULL)

            is Int       -> {
                objOut.writeByte(INT)
                objOut.writeInt(obj)
            }

            is Float     -> {
                objOut.writeByte(FLOAT)
                objOut.writeFloat(obj)
            }

            is String    -> {
                objOut.writeByte(STRING)
                val offset = strings[obj]
                if (offset == null) {
                    strings[obj] = strOut.size()
                    val bytes = obj.toByteArray(Charsets.UTF_8)
                    strOut.writeShort(bytes.size + 1)
                    strOut.write(bytes)
                    strOut.write(0)
                }
                objOut.writeInt(strings[obj]!!)
            }

            is List<*>   -> {
                objOut.writeByte(ARRAY)
                objOut.writeInt(obj.size)
                obj.forEach { o -> q.add(o ?: NULL_MARKER) }
            }

            is Boolean   -> {
                objOut.writeByte(BOOLEAN)
                objOut.writeByte(if (obj) 1 else 0)
            }

            is Map<*, *> -> {
                objOut.writeByte(MAP)
                objOut.writeInt(obj.size)
                obj.entries.forEach { (k, v) ->
                    q.add(k ?: NULL_MARKER); q.add(v ?: NULL_MARKER) }
            }

            is Long      -> {
                objOut.writeByte(LONG)
                objOut.writeLong(obj)
            }

            is Double    -> {
                objOut.writeByte(DOUBLE)
                objOut.writeDouble(obj)
            }

            is Char      -> {
                objOut.writeByte(CHAR)
                objOut.writeInt(obj.code)
            }

            else         ->
                throw IllegalArgumentException("Unsupported type ${obj?.javaClass} '$obj'")
        }
    }

    fun deserialize(data: ByteArray): Any? {
        val buf = ByteBuffer.wrap(data)
        val marker1 = buf.getInt(0)
        val strings = if (marker1 == STRINGS_MARKER) {
            buf.int // swallow the marker
            readStrings(buf)
        } else {
            emptyMap()
        }
        val marker2 = buf.int // swallow the marker
        if (marker2 != OBJECTS_MARKER) {
            throw IllegalArgumentException("expected data marker, got $marker2")
        }
        return readObjects(buf, strings)
    }

    private fun readStrings(buf: ByteBuffer): Map<Int, String> {
        val strings = mutableMapOf<Int, String>()
        val strBufferLen = buf.int
        val offset = buf.position()
        while (buf.position() - offset < strBufferLen) {
            val pos = buf.position() - offset
            val strLen = buf.short.toInt() - 1 // ignore \0 byte
            val strBytes = ByteArray(strLen)
            buf.get(strBytes)
            strings[pos] = String(strBytes, Charsets.UTF_8)
            buf.get()  // swallow \0 byte
        }
        return strings
    }

    private fun readObjects(buf: ByteBuffer, strings: Map<Int, String>): Any? {
        val objBufferLen = buf.int
        if (objBufferLen > buf.remaining()) {
            throw IllegalArgumentException("expect $objBufferLen bytes got ${buf.remaining()}")
        }

        val container = ArrayDeque<Container>()
        val r = readObject(buf, strings, container)
        while (container.isNotEmpty()) {
            container.pollFirst()?.read(buf, strings, container)
        }

        return r
    }

    private fun readObject(buf: ByteBuffer, strings: Map<Int, String>, q: Queue<Container>): Any? {
        when (buf.get().toInt()) {
            NULL    -> return null
            INT     -> return buf.int
            FLOAT   -> return buf.float

            STRING  -> {
                val offset = buf.int
                return strings[offset]!!
            }

            ARRAY   -> {
                val arraySize = buf.int
                val array = mutableListOf<Any?>()
                // We will populate the array with arraySize objects from the object list later,
                // when we take the ListContainer from the queue.
                q.add(ListContainer(arraySize, array))
                return array
            }

            BOOLEAN -> return buf.get() > 0

            MAP     -> {
                val mapSize = buf.int
                val map = mutableMapOf<Any, Any?>()
                q.add(MapContainer(mapSize, map))
                return map
            }

            LONG    -> return buf.long
            DOUBLE  -> return buf.double
            CHAR    -> return Char(buf.int)
            else    -> return null
        }
    }
}