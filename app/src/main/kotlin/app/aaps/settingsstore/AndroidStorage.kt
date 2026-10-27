package app.aaps.settingsstore

import android.content.Context
import androidx.datastore.core.CorruptionException
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.shared.impl.sharedPreferences.EncryptedKeyValueStore
import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import app.aaps.shared.impl.sharedPreferences.defaultPreferences
import app.aaps.shared.impl.sharedPreferences.openEncryptedStore
import app.aaps.shared.impl.sharedPreferences.preferencesFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The phone's settings store, opened once, as the first thing the app does. The counterpart of desktop's
 * `DesktopStorage` and `IosStorage`.
 *
 * One file, encrypted with a key in the Android Keystore ([KeystoreStoreCipher]), in the app's `no_backup`
 * folder. Android never puts that folder in a backup or in a transfer to a new phone, and the key stays on
 * this phone anyway. The first open moves the settings over from the old SharedPreferences file
 * ([SharedPreferencesMigration]).
 *
 * ## When the store cannot be opened
 *
 * [open] does not throw. The app gets an empty store that lives in memory only, and [Opened.failure] says
 * why. So nothing crashes, and nothing the app writes can replace the file that could not be read. `MainApp`
 * then starts nothing - no plugins, so no loop - and `ComposeMainActivity` shows the way out: try again, or
 * start fresh.
 */
object AndroidStorage {

    /** The Keystore alias of the store's key. Different from every `SecureEncrypt` alias. */
    const val STORE_KEY_ALIAS = "AapsSettingsStore"
    private const val FILE_NAME = "preferences.store"
    private const val MOVED_MARKER = "preferences.store.moved"

    /**
     * The store the app uses, and why it is the empty one in memory when it is.
     *
     * @param failure null when the real store is open. Otherwise a [StoreUnreadableException] when the file
     *   does not open with the key this phone has, or what else went wrong, such as the Keystore not
     *   answering.
     * @param notes what happened while opening, for the log. The app's logger cannot exist yet at that
     *   moment: it reads its own settings from this store.
     */
    class Opened internal constructor(val store: KeyValueStore, val failure: Exception?, val notes: List<String>, private val job: Job?) {

        /** For tests: DataStore allows a second store on the same file only after the first is gone. */
        internal fun close() = runBlocking { job?.cancelAndJoin() }
    }

    /** Where the store is, where its key is, and where the old settings are. Only tests use another one. */
    internal class Setup(val directory: File, val keys: KeystoreStoreCipher.Keys, val oldFile: File, val readOld: () -> Map<String, *>)

    @Volatile private var opened: Opened? = null
    private var setup: Setup? = null

    /** Told about a save that failed after the start. Set by `MainApp` once its logger exists. */
    @Volatile var onSaveFailure: (Throwable) -> Unit = {}

    /** Why the settings could not be opened, or null when they were (or have not been opened yet). */
    val failure: Exception? get() = opened?.failure

    /** Opens the store, or returns it if already open. Never throws: see the class description. */
    @Synchronized
    fun open(context: Context): Opened = opened ?: openWith(currentSetup(context)).also { opened = it }

    /**
     * Sets the unreadable store aside and removes its key, so the next start begins with empty settings. The
     * file is renamed, not deleted. Only for a start whose store could not be opened.
     *
     * Returns the file it was moved to.
     */
    @Synchronized
    fun startFresh(context: Context): File = startFreshWith(currentSetup(context))

    /** For tests that build the app graph without Android: every graph then gets [store]. */
    @Synchronized
    internal fun useForTests(store: KeyValueStore) {
        if (opened == null) opened = Opened(store, null, emptyList(), null)
    }

    internal fun openWith(setup: Setup): Opened {
        setup.directory.mkdirs()
        val notes = mutableListOf<String>()
        val storeFile = File(setup.directory, FILE_NAME)
        val cipher = KeystoreStoreCipher(setup.keys)
        val migration = SharedPreferencesMigration(setup.oldFile, setup.readOld, storeFile, cipher, File(setup.directory, MOVED_MARKER)) { notes += it }
        val job = SupervisorJob()
        return try {
            val store = openEncryptedStore(storeFile.absolutePath, cipher, listOf(migration), CoroutineScope(Dispatchers.IO + job)) { onSaveFailure(it) }
            Opened(store, null, notes, job)
        } catch (e: Exception) {
            // Shut the failed DataStore down completely: a second open of the same file - after "Start
            // fresh" - is refused while the first one is alive.
            runBlocking { job.cancelAndJoin() }
            Opened(EncryptedKeyValueStore.inMemory(CoroutineScope(Dispatchers.IO + SupervisorJob())), classify(e, storeFile), notes, null)
        }
    }

    internal fun startFreshWith(setup: Setup): File {
        val storeFile = File(setup.directory, FILE_NAME)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val setAside = File(setup.directory, "$FILE_NAME.unreadable-$stamp")
        if (storeFile.exists() && !storeFile.renameTo(setAside)) error("Could not rename ${storeFile.path}")
        setup.keys.delete()
        return setAside
    }

    /** Finds what the failure really was: DataStore may wrap it. */
    internal fun classify(e: Exception, storeFile: File): Exception {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is CorruptionException) return StoreUnreadableException(storeFile.path, cause)
            cause = cause.cause
        }
        return e
    }

    private fun currentSetup(context: Context): Setup = setup ?: appSetup(context.applicationContext).also { setup = it }

    private fun appSetup(context: Context) = Setup(
        directory = context.noBackupFilesDir,
        keys = AndroidKeystoreKeys(STORE_KEY_ALIAS),
        oldFile = File(File(context.dataDir, "shared_prefs"), "${preferencesFileName(context)}.xml"),
        readOld = { defaultPreferences(context).all }
    )
}
