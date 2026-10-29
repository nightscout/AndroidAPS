package app.aaps.implementation.utils.fabric

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.fabric.CrashReporter
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.core.keys.BooleanKey
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reporting opt-out on iOS, which is the part of this that must never drift.
 *
 * Two things are pinned. Reporting is **off** until the user asks for it, unlike Android where the
 * same stored key defaults to on - a phone that has never been asked must not start uploading. And
 * when it is off, nothing reaches the reporter at all, so an opt-out means opted out rather than
 * merely quieter.
 */
@OptIn(ExperimentalForeignApi::class)
class FabricPrivacyImplTest {

    /** Records what would have been sent, so the test can assert that nothing was. */
    private class RecordingReporter : CrashReporter {

        var collectionEnabled: Boolean? = null
        val messages = mutableListOf<String>()
        val exceptions = mutableListOf<String>()
        val keys = mutableListOf<String>()

        override fun isAvailable(): Boolean = true
        override fun setCollectionEnabled(enabled: Boolean) {
            collectionEnabled = enabled
        }

        override fun log(message: String) {
            messages += message
        }

        override fun setCustomKey(key: String, value: String) {
            keys += "$key=$value"
        }

        override fun recordException(type: String, message: String, stackTrace: List<String>) {
            exceptions += "$type: $message"
        }
    }

    /** An in-memory store, so the test says what is stored rather than what a device happens to hold. */
    private class FakeStore : KeyValueStore {

        val values = mutableMapOf<String, Any>()

        private inner class FakeEditor : KeyValueStore.Editor {

            override fun clear() = values.clear()
            override fun remove(key: String) {
                values.remove(key)
            }

            override fun putBoolean(key: String, value: Boolean) {
                values[key] = value
            }

            override fun putDouble(key: String, value: Double) {
                values[key] = value
            }

            override fun putLong(key: String, value: Long) {
                values[key] = value
            }

            override fun putInt(key: String, value: Int) {
                values[key] = value
            }

            override fun putString(key: String, value: String) {
                values[key] = value
            }
        }

        override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) = FakeEditor().block()
        override fun getAll(): Map<String, *> = values
        override fun clear() = values.clear()
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun remove(key: String) {
            values.remove(key)
        }

        override fun getString(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun getDouble(key: String, defaultValue: Double): Double = values[key] as? Double ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun putDouble(key: String, value: Double) {
            values[key] = value
        }

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun putLong(key: String, value: Long) {
            values[key] = value
        }

        override fun incInt(key: String) {
            values[key] = getInt(key, 0) + 1
        }

        override fun incLong(key: String) {
            values[key] = getLong(key, 0) + 1
        }
    }

    private val fileName = "unit-test-fabric.log"
    private val reporter = RecordingReporter()
    private val store = FakeStore()
    private val fabric = FabricPrivacyImpl(AAPSLoggerIos(fileName = fileName), store, reporter)

    @AfterTest
    fun removeTheLog() {
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
            ?.let { NSFileManager.defaultManager.removeItemAtPath("$it/$fileName", null) }
    }

    @Test
    fun `reporting is off on a phone that has never been asked`() {
        assertFalse(fabric.fabricEnabled(), "iOS must not start uploading before the user says so")
    }

    /** The shared key still says true. iOS deliberately does not follow it, and that is the point. */
    @Test
    fun `the shared default is not the one iOS uses`() {
        assertTrue(BooleanKey.MaintenanceEnableFabric.defaultValue, "the shared default changed - revisit the iOS one")
        assertFalse(fabric.fabricEnabled())
    }

    @Test
    fun `a stored yes is honoured`() {
        store.putBoolean(BooleanKey.MaintenanceEnableFabric.key, true)

        assertTrue(fabric.fabricEnabled())
    }

    @Test
    fun `a stored no is honoured`() {
        store.putBoolean(BooleanKey.MaintenanceEnableFabric.key, false)

        assertFalse(fabric.fabricEnabled())
    }

    @Test
    fun `nothing reaches the reporter while reporting is off`() {
        fabric.logMessage("a breadcrumb")
        fabric.logException(IllegalStateException("the pump said no"))
        fabric.setUserProperty("Pump", "Virtual")

        assertEquals(emptyList(), reporter.messages, "a breadcrumb was sent although reporting is off")
        assertEquals(emptyList(), reporter.exceptions, "an exception was sent although reporting is off")
        assertEquals(emptyList(), reporter.keys, "a custom key was sent although reporting is off")
    }

    @Test
    fun `the exception is reported with its type and message once reporting is on`() {
        store.putBoolean(BooleanKey.MaintenanceEnableFabric.key, true)

        fabric.logException(IllegalStateException("the pump said no"))

        assertEquals(listOf("IllegalStateException: the pump said no"), reporter.exceptions)
    }
}
