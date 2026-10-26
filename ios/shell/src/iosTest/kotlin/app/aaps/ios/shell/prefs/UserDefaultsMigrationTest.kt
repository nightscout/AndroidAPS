package app.aaps.ios.shell.prefs

import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.implementation.protection.KeychainStoreCipher
import app.aaps.shared.impl.sharedPreferences.EncryptedKeyValueStore
import app.aaps.shared.impl.sharedPreferences.openEncryptedStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The move from `NSUserDefaults` into the encrypted store, through DataStore and the real AES cipher.
 *
 * Against a defaults suite of its own rather than the standard defaults, so a run cannot touch what an
 * older build left on the simulator.
 */
class UserDefaultsMigrationTest {

    private val suite = "app.aaps.test.migration.${Random.nextLong().toULong()}"
    private val defaults = NSUserDefaults(suiteName = suite)
    private val dir = "${NSTemporaryDirectory().trimEnd('/')}/aaps-migration-test-${Random.nextLong().toULong()}"
    private val path = "$dir/preferences.store"
    private val cipher = KeychainStoreCipher(MemoryKeychain(), "preferences")
    private var job: Job? = null

    @OptIn(ExperimentalForeignApi::class)
    @AfterTest fun cleanUp() {
        close()
        defaults.removePersistentDomainForName(suite)
        NSFileManager.defaultManager.removeItemAtPath(dir, null)
    }

    private fun open(): EncryptedKeyValueStore {
        close()
        val newJob = SupervisorJob().also { job = it }
        val migration = UserDefaultsMigration(defaults, suite, path, cipher, AAPSLoggerIos(fileName = "aaps-test.log"))
        return openEncryptedStore(path, cipher, listOf(migration), CoroutineScope(Dispatchers.Default + newJob)) { }
    }

    private fun close() = runBlocking {
        job?.cancelAndJoin()
        job = null
    }

    @Test
    fun `every kind of value IosSp wrote moves with its type`() {
        defaults.setObject("mmol", forKey = "units")
        defaults.setBool(true, forKey = "closed")
        defaults.setInteger(5, forKey = "dia")
        defaults.setInteger(1_793_008_500_000L, forKey = "since")
        defaults.setDouble(0.1, forKey = "isf")
        defaults.setDouble(5.0, forKey = "target")

        val store = open()

        assertEquals("mmol", store.getString("units", ""))
        assertTrue(store.getBoolean("closed", false))
        assertEquals(5, store.getInt("dia", 0))
        assertEquals(1_793_008_500_000L, store.getLong("since", 0L))
        assertEquals(0.1, store.getDouble("isf", 0.0))
        assertEquals(5.0, store.getDouble("target", 0.0))
        // Typed, not text: a true/false stays a Boolean and a decimal a Double.
        assertEquals(true, store.getAll()["closed"])
        assertEquals(0.1, store.getAll()["isf"])
    }

    @Test
    fun `the moved keys leave NSUserDefaults and what the store cannot hold stays`() {
        defaults.setObject("secret-token-value", forKey = "nsclient_token")
        defaults.setObject(NSDate(), forKey = "not_ours")

        val store = open()

        assertNull(defaults.objectForKey("nsclient_token"))
        assertNotNull(defaults.objectForKey("not_ours"))
        assertFalse(store.contains("not_ours"))
    }

    @Test
    fun `the moved values are on the disk`() {
        defaults.setObject("mmol", forKey = "units")
        open()

        val reopened = open()

        assertEquals("mmol", reopened.getString("units", ""))
    }

    @Test
    fun `values already in the store win over NSUserDefaults`() {
        open().apply {
            putString("units", "mgdl")
            flush()
        }
        // A start that was cut short after the store was written but before the defaults were cleared.
        defaults.setObject("mmol", forKey = "units")
        defaults.setInteger(5, forKey = "dia")

        val store = open()

        assertEquals("mgdl", store.getString("units", ""))
        assertEquals(5, store.getInt("dia", 0))
        assertNull(defaults.objectForKey("units"))
    }

    @Test
    fun `nothing in NSUserDefaults opens an empty store`() {
        assertEquals(emptyMap<String, Any>(), open().getAll())
    }
}
