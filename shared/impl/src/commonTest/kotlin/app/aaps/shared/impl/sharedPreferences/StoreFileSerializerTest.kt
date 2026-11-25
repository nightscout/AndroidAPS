package app.aaps.shared.impl.sharedPreferences

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.Sink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StoreFileSerializerTest {

    private val cipher = FakeStoreCipher()

    /**
     * DataStore syncs the file before it closes the sink. Whatever is still in the sink then is written
     * after the sync, and a hard reboot can lose it (issue #5562).
     */
    @Test
    fun `a save leaves nothing in the sink for DataStore to write after its sync`() = runTest {
        val values: Map<String, Any> = (1..2000).associate { "key $it" to "value $it" }
        val expected = StoreCodec.encode(values, cipher).size.toLong()
        // Only bytes after the last full 8 KiB segment stay in the sink, so the test needs some.
        assertNotEquals(0L, expected % 8192)
        val file = Buffer()
        val sink: BufferedSink = (file as Sink).buffer()

        StoreFileSerializer(cipher).writeTo(values, sink)

        assertEquals(0L, sink.buffer.size)
        assertEquals(expected, file.size)
    }
}
