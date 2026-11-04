package app.aaps.shared.impl.sharedPreferences

import androidx.datastore.core.CorruptionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class StoreCodecTest {

    private val cipher = FakeStoreCipher()

    @Test
    fun `every value type reads back as the same type and value`() {
        val values: Map<String, Any> = mapOf(
            "text" to "mmol",
            "empty" to "",
            "unicode" to "Žluťoučký kůň \"quoted\" : [x]",
            "flag" to true,
            "int" to Int.MIN_VALUE,
            "long" to Long.MAX_VALUE,
            "isf" to 0.1,
            "rate" to 4.3,
            "negative" to -12.75,
            "key with : and \" in it" to 1
        )

        val decoded = StoreCodec.decode(StoreCodec.encode(values, cipher), cipher)

        assertEquals(values, decoded)
        decoded.forEach { (key, value) -> assertEquals(values.getValue(key)::class, value::class, key) }
    }

    @Test
    fun `an empty store reads back empty`() {
        assertEquals(emptyMap(), StoreCodec.decode(StoreCodec.encode(emptyMap(), cipher), cipher))
    }

    @Test
    fun `key names and values do not appear in the stored bytes`() {
        val bytes = StoreCodec.encode(mapOf("nsclient_token" to "secret-token-value"), cipher).decodeToString()

        assertFalse(bytes.contains("nsclient_token"))
        assertFalse(bytes.contains("secret-token-value"))
    }

    @Test
    fun `data that is not a store is reported as corrupt`() {
        assertFailsWith<CorruptionException> { StoreCodec.decode("units=mmol".encodeToByteArray(), cipher) }
        assertFailsWith<CorruptionException> { StoreCodec.decode(ByteArray(0), cipher) }
    }

    @Test
    fun `an unknown version is reported as corrupt`() {
        val bytes = StoreCodec.encode(mapOf("a" to 1), cipher)
        bytes[3] = 9

        assertFailsWith<CorruptionException> { StoreCodec.decode(bytes, cipher) }
    }

    @Test
    fun `data made with another key is reported as corrupt`() {
        val bytes = StoreCodec.encode(mapOf("a" to 1), FakeStoreCipher(key = 0x11))

        assertFailsWith<CorruptionException> { StoreCodec.decode(bytes, cipher) }
    }

    @Test
    fun `a key store that does not answer is passed on and not called corrupt`() {
        val bytes = StoreCodec.encode(mapOf("a" to 1), cipher)
        val locked = IllegalStateException("keyring locked")
        cipher.unavailable = locked

        val thrown = assertFailsWith<IllegalStateException> { StoreCodec.decode(bytes, cipher) }

        assertSame(locked, thrown)
    }

    @Test
    fun `a value type the store does not know is refused when written`() {
        assertFailsWith<IllegalArgumentException> { StoreCodec.encode(mapOf("float" to 0.1f), cipher) }
    }
}
