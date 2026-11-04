package app.aaps.ios.shell.prefs

import androidx.datastore.core.CorruptionException
import app.aaps.core.interfaces.logging.LTag
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.implementation.protection.AppleKeychain
import app.aaps.implementation.protection.Keychain
import app.aaps.implementation.protection.KeychainStoreCipher
import app.aaps.implementation.protection.KeychainUnavailableException
import app.aaps.shared.impl.sharedPreferences.EncryptedKeyValueStore
import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import app.aaps.shared.impl.sharedPreferences.openEncryptedStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import kotlin.time.Clock

/**
 * The iOS settings store, opened once, before the graph is built. The counterpart of desktop's
 * `DesktopStorage`.
 *
 * The settings are one file, encrypted with a key kept in the Keychain ([KeychainStoreCipher]), in
 * `Application Support/aaps`. That folder is kept out of iCloud and computer backups on purpose: the key
 * never leaves this phone, so no other phone could read the file anyway. A client restored onto a new
 * phone starts like a new install, and the setup wizard pairs it again.
 *
 * Opened before the graph, so that a store that cannot be opened stops the start with a clear message
 * instead of failing somewhere inside the first screen. Every graph - the app's and the diagnostic probe
 * graphs - then gets this same store: DataStore allows only one per file.
 *
 * The first open moves the old settings over from `NSUserDefaults` ([UserDefaultsMigration]). Nothing is
 * asked of the user.
 */
object IosStorage {

    /** The Keychain account of the store's own key. Its service is not the one `SecureEncrypt` uses. */
    const val STORE_KEY_ALIAS = "preferences"
    private const val KEYCHAIN_SERVICE = "app.aaps.settingsstore"
    private const val FILE_NAME = "preferences.store"

    /** Used only if the bundle has no identifier, which happens in a test binary, not in the app. */
    private const val FALLBACK_DOMAIN = "app.aaps"

    /** Where the store is and where its key and old settings are. Only tests use another one. */
    internal class Setup(val directory: String, val keychain: Keychain, val defaults: NSUserDefaults?, val domain: String)

    private val lock = NSRecursiveLock()
    private val log by lazy { AAPSLoggerIos() }
    private var setup: Setup? = null
    private var store: EncryptedKeyValueStore? = null

    val storePath: String get() = "${currentSetup().directory}/$FILE_NAME"

    /**
     * Opens the store, or returns it if already open.
     *
     * Throws [KeychainUnavailableException] when the Keychain does not answer, and
     * [StoreUnreadableException] when the file cannot be opened with the key this phone has.
     */
    fun open(): EncryptedKeyValueStore = locked { store ?: openNow().also { store = it } }

    /**
     * Sets the unreadable store aside and removes its key, so the next [open] starts with empty settings.
     * The file is renamed, not deleted.
     *
     * Returns the path it was moved to.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun startFresh(): String = locked {
        check(store == null) { "The settings store is open; there is nothing to start fresh from" }
        val path = storePath
        val setAside = "$path.unreadable-${Clock.System.now().toEpochMilliseconds()}"
        val files = NSFileManager.defaultManager
        if (files.fileExistsAtPath(path) && !files.moveItemAtPath(path, setAside, null)) error("Could not rename $path")
        currentSetup().keychain.delete(STORE_KEY_ALIAS)
        log.warn(LTag.CORE, "Settings store set aside as ${setAside.substringAfterLast('/')} and started fresh")
        setAside
    }

    /** For tests, before anything opens the store: a test binary has no Keychain, and must not read the app's defaults. */
    internal fun useForTests(testSetup: Setup) = locked { if (setup == null) setup = testSetup }

    private fun openNow(): EncryptedKeyValueStore {
        val setup = currentSetup()
        prepareDirectory(setup.directory)
        val path = storePath
        val cipher = KeychainStoreCipher(setup.keychain, STORE_KEY_ALIAS)
        val migrations = setup.defaults?.let { listOf(UserDefaultsMigration(it, setup.domain, path, cipher, log)) }.orEmpty()
        val job = SupervisorJob()
        return try {
            openEncryptedStore(path, cipher, migrations, CoroutineScope(Dispatchers.IO + job)) {
                log.error(LTag.CORE, "Settings could not be saved, the next change tries again: ${it.message}")
            }
        } catch (e: Exception) {
            // Shut the failed DataStore down completely before anything else happens: a second open of the
            // same file - after "Start fresh" - is refused while the first one is alive.
            runBlocking { job.cancelAndJoin() }
            throw classify(e)
        }
    }

    /** Finds what the failure really was: DataStore may wrap it. */
    internal fun classify(e: Exception): Exception {
        var cause: Throwable? = e
        while (cause != null) {
            when (cause) {
                is CorruptionException          -> return StoreUnreadableException(storePath, cause)
                is KeychainUnavailableException -> return cause
            }
            cause = cause.cause
        }
        return e
    }

    private fun currentSetup(): Setup = locked { setup ?: appSetup().also { setup = it } }

    private fun appSetup() = Setup(
        directory = "${applicationSupport()}/aaps",
        keychain = AppleKeychain(service = KEYCHAIN_SERVICE),
        defaults = NSUserDefaults.standardUserDefaults,
        domain = NSBundle.mainBundle.bundleIdentifier ?: FALLBACK_DOMAIN
    )

    private fun applicationSupport(): String =
        NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("iOS gave no Application Support directory")

    /** Creates the folder and keeps it out of backups - set on the folder, so it covers every file DataStore moves in. */
    @OptIn(ExperimentalForeignApi::class)
    private fun prepareDirectory(directory: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        val excluded = NSURL.fileURLWithPath(directory).setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        if (!excluded) log.warn(LTag.CORE, "Could not keep $directory out of backups")
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
