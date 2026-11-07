package app.aaps.plugins.dexcomoneplus.identity

import app.aaps.plugins.dexcomoneplus.session.OnePlusAuthRefusals
import kotlin.test.Test
import kotlin.test.assertEquals

/** The refusal count is kept in the slot store, so it outlives a session and an app restart. */
class OnePlusSensorStoreRefusalsTest {

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

    @Test
    fun `nothing stored reads as no refusals`() {
        assertEquals(OnePlusAuthRefusals(), OnePlusSensorStore(MemoryPrefs()).loadAuthRefusals())
    }

    @Test
    fun `a saved count reads back in a new store on the same file`() {
        val prefs = MemoryPrefs()
        val saved = OnePlusAuthRefusals(address = "AA:BB:CC:DD:EE:FF", count = 2, lastAtMs = 123L, lastWithSavedKey = true)
        OnePlusSensorStore(prefs).saveAuthRefusals(saved)
        assertEquals(saved, OnePlusSensorStore(prefs).loadAuthRefusals())
    }

    @Test
    fun `a cleared count leaves no keys behind`() {
        val prefs = MemoryPrefs()
        val store = OnePlusSensorStore(prefs)
        store.saveAuthRefusals(OnePlusAuthRefusals(address = "AA:BB:CC:DD:EE:FF", count = 3, lastAtMs = 1L))
        store.saveAuthRefusals(OnePlusAuthRefusals())
        assertEquals(emptyMap(), prefs.values)
    }
}
