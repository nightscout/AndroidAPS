package app.aaps.desktop.shell.secure

import androidx.datastore.core.CorruptionException
import app.aaps.core.interfaces.logging.LTag
import app.aaps.implementation.logging.AAPSLoggerDesktop
import app.aaps.implementation.maintenance.DesktopFolders
import app.aaps.shared.impl.sharedPreferences.EncryptedKeyValueStore
import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import app.aaps.shared.impl.sharedPreferences.openEncryptedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The desktop's key vault and settings store, opened once, before the graph is built.
 *
 * Before the graph, so that a store that cannot be opened stops the start with a clear message instead
 * of failing somewhere inside the first screen. The graph's bindings then hand out the same objects.
 *
 * The first open on a machine moves the old plain storage over: key files into the OS vault
 * ([LegacyKeyFiles]) and `preferences.properties` into the encrypted store ([PropertiesFileMigration]).
 * Nothing is asked of the user.
 */
object DesktopStorage {

    /** The vault alias of the store's own key. Different from every `SecureEncrypt` alias. */
    const val STORE_KEY_ALIAS = "preferences"

    val storeFile: File get() = File(DesktopFolders.data, "preferences.store")
    private val propertiesFile: File get() = File(DesktopFolders.data, "preferences.properties")
    private val keysDir: File get() = File(DesktopFolders.data, "keys")

    class Opened(val vault: KeyVault, val fallbackReason: String?, val store: EncryptedKeyValueStore)

    private var opened: Opened? = null
    private var choice: KeyVaults.Choice? = null

    /**
     * Opens the vault and the store, or returns them if already open.
     *
     * Throws [KeyVaultUnavailableException] when the key store exists but does not answer, and
     * [StoreUnreadableException] when the store file cannot be opened with the key this computer has.
     */
    @Synchronized
    fun open(): Opened = opened ?: openNow().also { opened = it }

    private fun openNow(): Opened {
        val log = AAPSLoggerDesktop()
        OwnerOnlyFiles.restrictDir(DesktopFolders.data)
        val choice = choice ?: KeyVaults.pick(System.getProperty("os.name").orEmpty(), keysDir, "client${DesktopFolders.client}").also { choice = it }
        if (choice.fallbackReason == null) log.info(LTag.CORE, "Keys are kept in ${choice.vault.name}")
        else log.warn(LTag.CORE, "Keys are kept in ${choice.vault.name}, because ${choice.fallbackReason}")
        LegacyKeyFiles.migrate(keysDir, choice.vault, log)

        val cipher = VaultStoreCipher(choice.vault, STORE_KEY_ALIAS)
        val job = SupervisorJob()
        val store = try {
            openEncryptedStore(
                path = storeFile.absolutePath,
                cipher = cipher,
                migrations = listOf(PropertiesFileMigration(propertiesFile, storeFile, cipher, log)),
                scope = CoroutineScope(Dispatchers.IO + job),
                onSaveFailure = { log.error(LTag.CORE, "Settings could not be saved, the next change tries again: ${it.message}") }
            )
        } catch (e: Exception) {
            // Shut the failed DataStore down completely before anything else happens: a second open of
            // the same file - after "Start fresh" - is refused while the first one is alive.
            runBlocking { job.cancelAndJoin() }
            throw classify(e)
        }
        // A change made just before the window closes may still be waiting for the background save.
        Runtime.getRuntime().addShutdownHook(Thread({ store.flush() }, "settings-store-flush"))
        return Opened(choice.vault, choice.fallbackReason, store)
    }

    /**
     * Sets the unreadable store aside and removes its key, so the next [open] starts with empty
     * settings. The file is renamed, not deleted.
     *
     * Returns the file it was moved to.
     */
    @Synchronized
    fun startFresh(): File {
        check(opened == null) { "The settings store is open; there is nothing to start fresh from" }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val setAside = File(storeFile.parentFile, "${storeFile.name}.unreadable-$stamp")
        if (storeFile.exists() && !storeFile.renameTo(setAside)) error("Could not rename ${storeFile.path}")
        val vault = (choice ?: KeyVaults.pick(System.getProperty("os.name").orEmpty(), keysDir, "client${DesktopFolders.client}").also { choice = it }).vault
        vault.delete(STORE_KEY_ALIAS)
        AAPSLoggerDesktop().warn(LTag.CORE, "Settings store set aside as ${setAside.name} and started fresh")
        return setAside
    }

    /** Finds what the failure really was: DataStore may wrap it. */
    internal fun classify(e: Exception): Exception {
        var cause: Throwable? = e
        while (cause != null) {
            when (cause) {
                is CorruptionException          -> return StoreUnreadableException(storeFile.path, cause)
                is KeyVaultUnavailableException -> return cause
            }
            cause = cause.cause
        }
        return e
    }
}
