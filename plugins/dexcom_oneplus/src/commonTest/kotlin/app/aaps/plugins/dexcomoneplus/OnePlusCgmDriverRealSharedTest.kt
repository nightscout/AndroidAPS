package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefs
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerStub
import app.aaps.plugins.dexcomoneplus.session.OnePlusCpuLease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The real driver's own state, run on every platform the module builds for.
 *
 * Nothing here needs a radio: it is the part of the driver that decides.
 * The three things the move replaced - the lock, the counter and the clock - are all exercised.
 *
 * No test here connects. The fake platform's GATT client throws if it is ever asked for, so a test
 * that started a session by accident would say so instead of hanging on a radio that is not there.
 */
class OnePlusCgmDriverRealSharedTest {

    @Test
    fun `a fresh driver has no session and no store`() {
        val driver = OnePlusCgmDriverReal()
        assertNull(driver.sensorStore())
        assertFalse(driver.isSessionUp())
        assertEquals(OnePlusWarmupState.Phase.IDLE, driver.warmupState().phase)
        assertNull(driver.lastCalibrationOutcome())
        assertFalse(driver.calibrationPending())
        driver.shutdown()
    }

    @Test
    fun `attach gives the slot its own store file`() {
        val platform = FakePlatform()
        val production = OnePlusCgmDriverReal()
        val staging = OnePlusCgmDriverReal(storeNamespace = "staging")
        production.attach(platform)
        staging.attach(platform)
        assertNotNull(production.sensorStore())
        assertNotNull(staging.sensorStore())
        assertEquals(
            listOf(
                OnePlusSensorStore.prefsFileName(null),
                OnePlusSensorStore.prefsFileName("staging"),
            ),
            platform.prefsAsked,
        )
        production.shutdown()
        staging.shutdown()
    }

    @Test
    fun `a promotion points the instance at the production file under its own link`() {
        val platform = FakePlatform()
        val staging = OnePlusCgmDriverReal(storeNamespace = "staging")
        staging.attach(platform)
        staging.saveIdentity(OnePlusSensorIdentity(pin = "1234"))
        staging.rebindStore(null)
        assertEquals(OnePlusSensorStore.prefsFileName(null), platform.prefsAsked.last())
        // The production file is a different file, so the staging PIN is not in it.
        assertNull(staging.sensorStore()?.load()?.identity?.pin)
        staging.shutdown()
    }

    @Test
    fun `a pre-soak sensor is never calibrated`() {
        val driver = OnePlusCgmDriverReal(storeNamespace = "staging")
        driver.attach(FakePlatform())
        assertFalse(driver.offerCalibration(glucoseMgdl = 120, bloodAtMs = nowMs()))
        assertNull(driver.lastCalibrationOutcome())
        driver.shutdown()
    }

    @Test
    fun `a fingerstick is refused while there is no session`() {
        val driver = OnePlusCgmDriverReal()
        driver.attach(FakePlatform())
        assertFalse(driver.offerCalibration(glucoseMgdl = 120, bloodAtMs = nowMs()))
        assertFalse(driver.calibrationPending())
        driver.shutdown()
    }

    @Test
    fun `nothing is resumed when the store is empty`() {
        val driver = OnePlusCgmDriverReal()
        driver.attach(FakePlatform())
        assertFalse(driver.resumeStoredSession())
        driver.shutdown()
    }

    @Test
    fun `nothing is resumed when the stored session has no key`() {
        val platform = FakePlatform()
        val driver = OnePlusCgmDriverReal()
        driver.attach(platform)
        driver.saveIdentity(OnePlusSensorIdentity(pin = "1234"))
        driver.sensorStore()?.saveLastMac("AA:BB:CC:DD:EE:FF")
        assertFalse(driver.resumeStoredSession())
        driver.shutdown()
    }

    @Test
    fun `scan start and stop reach the scanner`() {
        val platform = FakePlatform()
        val driver = OnePlusCgmDriverReal()
        driver.attach(platform)
        driver.startScan { }
        assertTrue(platform.scanner.isScanning())
        driver.stopScan()
        assertFalse(platform.scanner.isScanning())
        driver.shutdown()
    }

    @Test
    fun `shutdown can be called twice`() {
        val driver = OnePlusCgmDriverReal()
        driver.attach(FakePlatform())
        driver.shutdown()
        driver.shutdown()
        assertFalse(driver.isSessionUp())
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** A host with a store and a scanner, and nothing that needs a radio. */
    private class FakePlatform : OnePlusPlatform {

        val prefsAsked = mutableListOf<String>()
        val scanner = OnePlusBleScannerStub()
        private val files = mutableMapOf<String, FakePrefs>()

        override fun sensorPrefs(fileName: String): OnePlusSensorPrefs {
            prefsAsked += fileName
            return files.getOrPut(fileName) { FakePrefs() }
        }

        override fun scanner(hint: OnePlusStoredSession?, slot: String): OnePlusBleScanner = scanner

        override fun gattClient(profile: OemDeviceProfile): OnePlusGattClient =
            throw IllegalStateException("a test asked for a GATT client; no test here connects")

        override fun cpuLease(slot: String): OnePlusCpuLease = NoLease

        private object NoLease : OnePlusCpuLease {

            override fun hold(maxMillis: Long): OnePlusCpuLease.Hold? = null
        }
    }

    /** In memory [OnePlusSensorPrefs]. A null string removes the key, as the real ones do. */
    private class FakePrefs : OnePlusSensorPrefs {

        private val values = mutableMapOf<String, Any>()

        override fun getString(key: String): String? = values[key] as? String

        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(block: OnePlusSensorPrefs.Editor.() -> Unit) {
            object : OnePlusSensorPrefs.Editor {
                override fun putString(key: String, value: String?) {
                    if (value == null) values.remove(key) else values[key] = value
                }

                override fun putLong(key: String, value: Long) {
                    values[key] = value
                }

                override fun putInt(key: String, value: Int) {
                    values[key] = value
                }

                override fun putBoolean(key: String, value: Boolean) {
                    values[key] = value
                }

                override fun remove(key: String) {
                    values.remove(key)
                }

                override fun clear() {
                    values.clear()
                }
            }.block()
        }
    }
}
