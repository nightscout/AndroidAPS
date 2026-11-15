package app.aaps.shared.impl.sharedPreferences

import androidx.datastore.core.CorruptionException
import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The file format of [EncryptedKeyValueStore]: the whole map in one encrypted block.
 *
 * ```
 * "AKV" | version (1 byte) | cipher.encrypt( {"key": ["type", "value as text"], ...} )
 * ```
 *
 * One block rather than one encrypted value per key, so the key names are hidden too, and there is
 * only one thing that can be damaged.
 *
 * The type is stored with each value. Android stores typed values, and a value read back as another
 * type than it was written must not change by accident. A Double is written with `toString()`, which
 * reads back to exactly the same Double.
 *
 * The header is not secret. It only lets a file that is not a store at all be told apart from a store
 * that cannot be opened with this device's key.
 */
object StoreCodec {

    private val MAGIC = byteArrayOf('A'.code.toByte(), 'K'.code.toByte(), 'V'.code.toByte())
    private const val VERSION: Byte = 1
    private const val HEADER_SIZE = 4

    private const val STRING = "s"
    private const val BOOLEAN = "b"
    private const val INT = "i"
    private const val LONG = "l"
    private const val DOUBLE = "d"

    fun encode(values: Map<String, Any>, cipher: StoreCipher): ByteArray {
        val json = JsonObject(values.mapValues { (key, value) -> JsonArray(listOf(JsonPrimitive(typeOf(key, value)), JsonPrimitive(value.toString()))) })
        return MAGIC + VERSION + cipher.encrypt(json.toString().encodeToByteArray())
    }

    /**
     * Reads what [encode] wrote.
     *
     * Throws [CorruptionException] when the data is not a store, has an unknown version, cannot be
     * opened with this device's key, or does not parse. Any other exception comes from the cipher not
     * being able to reach its key right now, and is passed on unchanged: the file may be fine.
     */
    fun decode(bytes: ByteArray, cipher: StoreCipher): Map<String, Any> {
        if (bytes.size < HEADER_SIZE || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
            throw CorruptionException("Not an AAPS settings store")
        val version = bytes[MAGIC.size]
        if (version != VERSION) throw CorruptionException("Unknown settings store version $version")
        val plain = try {
            cipher.decrypt(bytes.copyOfRange(HEADER_SIZE, bytes.size))
        } catch (e: SealedDataUnreadableException) {
            throw CorruptionException("The settings store cannot be opened with the key of this device", e)
        }
        return try {
            Json.parseToJsonElement(plain.decodeToString()).jsonObject.mapValues { (_, element) ->
                val pair = element.jsonArray
                valueOf(pair[0].jsonPrimitive.content, pair[1].jsonPrimitive.content)
            }
        } catch (e: CorruptionException) {
            throw e
        } catch (e: Exception) {
            throw CorruptionException("The settings store does not parse", e)
        }
    }

    private fun typeOf(key: String, value: Any): String = when (value) {
        is String  -> STRING
        is Boolean -> BOOLEAN
        is Int     -> INT
        is Long    -> LONG
        is Double  -> DOUBLE
        else       -> throw IllegalArgumentException("$key: a ${value::class.simpleName} cannot be stored")
    }

    private fun valueOf(type: String, text: String): Any = when (type) {
        STRING  -> text
        BOOLEAN -> text.toBooleanStrict()
        INT     -> text.toInt()
        LONG    -> text.toLong()
        DOUBLE  -> text.toDouble()
        else    -> throw CorruptionException("Unknown value type $type")
    }
}
