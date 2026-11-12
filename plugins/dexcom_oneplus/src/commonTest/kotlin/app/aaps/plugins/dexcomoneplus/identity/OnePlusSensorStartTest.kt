package app.aaps.plugins.dexcomoneplus.identity

import app.aaps.plugins.dexcomoneplus.parse.OnePlusGlucoseParser
import app.aaps.plugins.dexcomoneplus.parse.OnePlusSensorStart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The start of a sensor as the sensor reports it, and the mark that it was taken once for that sensor.
 * A sensor that was already running used to get the time AAPS connected to it as its start.
 *
 * Test names here carry no comma: Kotlin/Native rejects one inside a backtick quoted name.
 */
class OnePlusSensorStartTest {

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

    private val now = 1_800_000_000_000L
    private val threeDays = 3 * 24 * 3600

    @Test
    fun `a G7 glucose message gives the start of the sensor`() {
        assertEquals(now - threeDays * 1000L, OnePlusSensorStart.fromEgv(OnePlusGlucoseParser.OPCODE_EGV2, threeDays, now))
    }

    /** The 0x4f message counts from the transmitter: its age is not the age of the sensor. */
    @Test
    fun `the older glucose message gives no start`() {
        assertNull(OnePlusSensorStart.fromEgv(OnePlusGlucoseParser.OPCODE_EGV1, threeDays, now))
    }

    @Test
    fun `no session age gives no start`() {
        assertNull(OnePlusSensorStart.fromEgv(OnePlusGlucoseParser.OPCODE_EGV2, null, now))
        assertNull(OnePlusSensorStart.fromEgv(OnePlusGlucoseParser.OPCODE_EGV2, 0, now))
        // An unsigned age too large for an Int arrives negative.
        assertNull(OnePlusSensorStart.fromEgv(OnePlusGlucoseParser.OPCODE_EGV2, -5, now))
    }

    @Test
    fun `a new sensor start is not settled`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        assertFalse(store.isSessionStartSettled())
    }

    @Test
    fun `a settled start stays settled for the same sensor only`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.saveLastMac("AA:BB:CC:DD:EE:01")
        store.markSessionStartSettled()
        assertTrue(store.isSessionStartSettled())
        store.saveLastMac("AA:BB:CC:DD:EE:02")
        assertFalse(store.isSessionStartSettled())
    }

    @Test
    fun `nothing is settled without a sensor`() {
        val store = OnePlusSensorStore(MemoryPrefs())
        store.markSessionStartSettled()
        assertFalse(store.isSessionStartSettled())
    }
}
