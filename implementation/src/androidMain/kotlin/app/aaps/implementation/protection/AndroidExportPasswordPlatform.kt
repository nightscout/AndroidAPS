package app.aaps.implementation.protection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.protection.ExportPasswordPlatform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Keeps the encrypted export password in Jetpack DataStore.
 *
 * Deliberately not AAPS preferences: those are exported and restored, and a remembered password must
 * stay on the phone it was entered on. DataStore is local to the app on this device.
 *
 * **The names below are a stored format.** Changing [DATASTORE_NAME] or [PASSWORD_PREFERENCE_NAME]
 * does not fail anything - it silently stops finding what an existing install wrote, and every user
 * with a remembered password is asked for it again with no explanation.
 *
 * Reads and writes are wrapped in `runBlocking` because the interface is synchronous. They touch one
 * small local file, and the callers are already on a background thread doing an export.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidExportPasswordPlatform @Inject constructor(
    private val context: Context,
    private val config: Config,
    private val fileListProvider: FileListProvider
) : ExportPasswordPlatform {

    private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(
        name = DATASTORE_NAME
    )

    private val secretKey = stringPreferencesKey("$PASSWORD_PREFERENCE_NAME.key")
    private val timestampKey = stringPreferencesKey("$PASSWORD_PREFERENCE_NAME.ts")

    override fun read(): ExportPasswordPlatform.Stored? = runBlocking {
        val stored = context.dataStore.data.first()
        val secret = stored[secretKey] ?: return@runBlocking null
        if (secret.isEmpty()) return@runBlocking null
        ExportPasswordPlatform.Stored(secret, stored[timestampKey]?.toLongOrNull() ?: 0L)
    }

    override fun write(secret: String, timestamp: Long) {
        runBlocking {
            context.dataStore.edit {
                it[secretKey] = secret
                it[timestampKey] = timestamp.toString()
            }
        }
    }

    override fun clear() {
        runBlocking {
            context.dataStore.edit {
                it[secretKey] = ""
                it[timestampKey] = "0"
            }
        }
    }

    /**
     * Shortened only on an engineering dev build, and only when a marker file is present.
     *
     * Two files, so a tester can pick how long to wait: `DebugUnattendedExportDev` runs the whole
     * cycle in twenty minutes, `DebugUnattendedExport` over two days. Both are ignored on a release
     * build whatever the directory holds.
     */
    override fun shortenedValidity(): ExportPasswordPlatform.Validity? {
        if (!config.isEngineeringMode() || !config.isDev()) return null
        val extraDir = fileListProvider.ensureExtraDirExists() ?: return null
        return when {
            extraDir.findFile("DebugUnattendedExportDev") != null ->
                ExportPasswordPlatform.Validity(window = 20 * 60 * 1000L, gracePeriod = 10 * 60 * 1000L)

            extraDir.findFile("DebugUnattendedExport") != null    ->
                ExportPasswordPlatform.Validity(window = 2 * 24 * 3600 * 1000L, gracePeriod = 24 * 3600 * 1000L)

            else                                                  -> null
        }
    }

    private companion object {

        const val DATASTORE_NAME = "app.aaps.plugins.configuration.maintenance.ImportExport.datastore"
        const val PASSWORD_PREFERENCE_NAME = "$DATASTORE_NAME.unattended_export"
    }
}
