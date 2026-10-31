package app.aaps.desktop.shell.secure

import androidx.datastore.core.DataMigration
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import app.aaps.shared.impl.sharedPreferences.StoreCodec
import java.io.File
import java.util.Properties

/**
 * Moves the old plain `preferences.properties` (what `DesktopSp` wrote until 2026-10) into the
 * encrypted store, once.
 *
 * DataStore runs it before the store is first read, writes the result, and only then calls [cleanUp].
 * [cleanUp] still reads the written file back itself, and deletes the plain file only when every key
 * from it is there. If the start is cut short in between, the next start does the same again: values
 * already in the store win, because they can only be newer.
 *
 * Every value arrives as text, the way the properties file held it. The store's getters read text as
 * the type asked for, as `DesktopSp` did, and the next write of a key stores it with its real type.
 */
class PropertiesFileMigration(
    private val properties: File,
    private val storeFile: File,
    private val cipher: StoreCipher,
    private val log: AAPSLogger
) : DataMigration<Map<String, Any>> {

    private var migratedKeys: Set<String> = emptySet()

    override suspend fun shouldMigrate(currentData: Map<String, Any>): Boolean = properties.isFile

    override suspend fun migrate(currentData: Map<String, Any>): Map<String, Any> {
        val loaded = Properties().apply { properties.inputStream().use { load(it) } }
        val values: Map<String, Any> = loaded.stringPropertyNames().associateWith { loaded.getProperty(it) }
        migratedKeys = values.keys
        return values + currentData
    }

    override suspend fun cleanUp() {
        if (migratedKeys.isNotEmpty()) {
            val written = StoreCodec.decode(storeFile.readBytes(), cipher)
            val missing = migratedKeys - written.keys
            if (missing.isNotEmpty()) {
                log.error(LTag.CORE, "${missing.size} settings are not in the new store yet; ${properties.path} is kept")
                return
            }
        }
        properties.delete()
        File(properties.parentFile, "${properties.name}.new").delete()
        log.info(LTag.CORE, "Moved ${migratedKeys.size} settings from ${properties.name} into the encrypted store")
    }
}
