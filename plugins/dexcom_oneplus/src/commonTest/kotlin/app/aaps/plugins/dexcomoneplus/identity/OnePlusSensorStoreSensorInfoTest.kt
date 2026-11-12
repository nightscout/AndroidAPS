package app.aaps.plugins.dexcomoneplus.identity

import app.aaps.plugins.dexcomoneplus.parse.OnePlusExtendedVersionRx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session length a sensor reported belongs to that sensor only, and is asked a few times at most.
 *
 * Test names here carry no comma: Kotlin/Native rejects one inside a backtick quoted name.
 */
class OnePlusSensorStoreSensorInfoTest {

    private class MemoryPrefs : OnePlusSensorPrefs {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String): String? = values[key] as? String
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(block: OnePlusSensorPrefs.Editor.() -> Unit) {
            object : OnePlusSensorPrefs.Editor {
                override fun putString(key: String, value: String?) {
                    values[key] = value
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

    private val fifteenDay = OnePlusExtendedVersionRx(sessionLengthSeconds = 1_339_200L, warmupSeconds = 3_720)

    @Test
    fun `nothing is known before the sensor answered`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        assertNull(store.loadSensorInfo())
        assertTrue(store.shouldAskSensorInfo())
    }

    @Test
    fun `a saved answer reads back and is not asked again`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        store.saveSensorInfo(fifteenDay)
        assertEquals(fifteenDay, store.loadSensorInfo())
        assertFalse(store.shouldAskSensorInfo())
    }

    @Test
    fun `another sensor does not inherit the answer`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        store.saveSensorInfo(fifteenDay)
        store.saveLastMac("AA:BB:CC:DD:EE:02")
        assertNull(store.loadSensorInfo())
        assertTrue(store.shouldAskSensorInfo())
    }

    @Test
    fun `without a sensor MAC nothing is saved or asked`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveSensorInfo(fifteenDay)
        assertNull(store.loadSensorInfo())
        assertFalse(store.shouldAskSensorInfo())
    }

    @Test
    fun `a sensor is asked three times at most`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        repeat(OnePlusSensorStore.MAX_SENSOR_INFO_ASKS) {
            assertTrue(store.shouldAskSensorInfo())
            store.noteSensorInfoAsked()
        }
        assertFalse(store.shouldAskSensorInfo())
    }

    @Test
    fun `the count starts again for a new sensor`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        repeat(OnePlusSensorStore.MAX_SENSOR_INFO_ASKS) { store.noteSensorInfoAsked() }
        store.saveLastMac("AA:BB:CC:DD:EE:02")
        assertTrue(store.shouldAskSensorInfo())
    }

    @Test
    fun `a promoted sensor keeps its own answer`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        store.saveSensorInfo(OnePlusExtendedVersionRx(sessionLengthSeconds = 907_200L, warmupSeconds = 1_620))
        val promoted = OnePlusStoredSession(
            identity = OnePlusSensorIdentity(pin = "1234", serial = "S2", gtin = null, rawGs1 = null),
            lastMac = "aa:bb:cc:dd:ee:02",
            lastDeviceName = "DXCM02",
            sharedKey = null,
        )
        store.adopt(promoted, sessionStartMs = 1_000L, sensorInfo = fifteenDay)
        assertEquals(fifteenDay, store.loadSensorInfo())
    }

    @Test
    fun `a promoted sensor without an answer drops the old one`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        store.saveSensorInfo(fifteenDay)
        val promoted = OnePlusStoredSession(
            identity = OnePlusSensorIdentity(pin = "1234", serial = "S2", gtin = null, rawGs1 = null),
            lastMac = "AA:BB:CC:DD:EE:02",
            lastDeviceName = "DXCM02",
            sharedKey = null,
        )
        store.adopt(promoted, sessionStartMs = 1_000L)
        assertNull(store.loadSensorInfo())
    }
}
