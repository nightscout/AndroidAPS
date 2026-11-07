package app.aaps.ios.shell.prefs

import androidx.datastore.core.DataMigration
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import app.aaps.shared.impl.sharedPreferences.StoreCodec
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * Moves the settings that `IosSp` kept in `NSUserDefaults` (until 2026-10) into the encrypted store, once.
 *
 * DataStore runs it before the store is first read, writes the result, and only then calls [cleanUp].
 * [cleanUp] reads the written file back and removes the moved keys from `NSUserDefaults` only when all of
 * them are there. If the start is cut short in between, the next start does the same again: values
 * already in the store win, because they can only be newer.
 *
 * What moves is what `IosSp` could write: text, true/false, whole numbers and decimals. Anything else in
 * the app's defaults - a date, data, a list - was not written by AAPS and stays where it is. A whole
 * number moves as a Long, because `NSUserDefaults` keeps Int and Long the same way; the store reads a
 * Long back as an Int when asked for one.
 */
class UserDefaultsMigration(
    private val defaults: NSUserDefaults,
    /** The app's own domain, the bundle id. Its persistent domain holds only what the app wrote. */
    private val domain: String,
    private val storePath: String,
    private val cipher: StoreCipher,
    private val log: AAPSLogger
) : DataMigration<Map<String, Any>> {

    private var movedKeys: Set<String> = emptySet()

    override suspend fun shouldMigrate(currentData: Map<String, Any>): Boolean = values().isNotEmpty()

    override suspend fun migrate(currentData: Map<String, Any>): Map<String, Any> {
        val values = values()
        movedKeys = values.keys
        return values + currentData
    }

    override suspend fun cleanUp() {
        // Never throws: DataStore would fail the whole start for it, while the next start simply tries again.
        try {
            val missing = movedKeys - StoreCodec.decode(readStoreFile(), cipher).keys
            if (missing.isNotEmpty()) {
                log.error(LTag.CORE, "${missing.size} settings are not in the new store yet; they stay in NSUserDefaults")
                return
            }
            movedKeys.forEach { defaults.removeObjectForKey(it) }
            log.info(LTag.CORE, "Moved ${movedKeys.size} settings from NSUserDefaults into the encrypted store")
        } catch (e: Exception) {
            log.error(LTag.CORE, "Could not check the moved settings; they stay in NSUserDefaults: ${e.message}")
        }
    }

    /** The values of the app's domain that the store can hold. */
    internal fun values(): Map<String, Any> =
        defaults.persistentDomainForName(domain).orEmpty().entries.mapNotNull { (key, value) ->
            val name = key as? String ?: return@mapNotNull null
            storable(value)?.let { name to it }
        }.toMap()

    @OptIn(ExperimentalForeignApi::class)
    private fun readStoreFile(): ByteArray {
        val data = NSFileManager.defaultManager.contentsAtPath(storePath) ?: error("$storePath was not written")
        return data.bytes?.readBytes(data.length.toInt()) ?: ByteArray(0)
    }

    companion object {

        /**
         * A value of `NSUserDefaults` as the store holds it, or null when the store cannot hold it.
         *
         * A number read back from the defaults file is an `NSNumber` that only its type code describes:
         * `c` (or `B`) is a true/false, `f` and `d` are decimals, every other code is a whole number. The
         * Kotlin types come first for a value still in memory from this run.
         */
        @OptIn(ExperimentalForeignApi::class)
        internal fun storable(value: Any?): Any? = when (value) {
            is String, is Boolean, is Long, is Double -> value
            is Int                                    -> value.toLong()
            is NSNumber                               -> when (value.objCType?.toKString()) {
                "c", "B" -> value.boolValue
                "f", "d" -> value.doubleValue
                else     -> value.longLongValue
            }

            else                                      -> null
        }
    }
}
