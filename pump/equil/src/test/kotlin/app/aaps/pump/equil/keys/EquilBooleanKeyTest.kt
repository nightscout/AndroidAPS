package app.aaps.pump.equil.keys

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquilBooleanKeyTest : TestBase() {

    @Test
    fun `all enum values should be accessible`() {
        val allKeys = EquilBooleanKey.entries
        assertEquals(4, allKeys.size)
        assert(allKeys.contains(EquilBooleanKey.BasalSet))
        assert(allKeys.contains(EquilBooleanKey.AlarmBattery10))
        assert(allKeys.contains(EquilBooleanKey.AlarmInsulin10))
        assert(allKeys.contains(EquilBooleanKey.AlarmInsulin5))
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(EquilBooleanKey.BasalSet, EquilBooleanKey.valueOf("BasalSet"))
        assertEquals(EquilBooleanKey.AlarmBattery10, EquilBooleanKey.valueOf("AlarmBattery10"))
        assertEquals(EquilBooleanKey.AlarmInsulin10, EquilBooleanKey.valueOf("AlarmInsulin10"))
        assertEquals(EquilBooleanKey.AlarmInsulin5, EquilBooleanKey.valueOf("AlarmInsulin5"))
    }

    @Test
    fun `BasalSet should have correct properties`() {
        assertEquals("key_equil_basal_set", EquilBooleanKey.BasalSet.key)
        assertFalse(EquilBooleanKey.BasalSet.defaultValue)
        assertTrue(EquilBooleanKey.BasalSet.exportable)
    }

    @Test
    fun `AlarmBattery10 should have correct properties`() {
        assertEquals("key_equil_alarm_battery_10", EquilBooleanKey.AlarmBattery10.key)
        assertFalse(EquilBooleanKey.AlarmBattery10.defaultValue)
        assertTrue(EquilBooleanKey.AlarmBattery10.exportable)
    }

    @Test
    fun `AlarmInsulin10 should have correct properties`() {
        assertEquals("key_equil_alarm_insulin_10", EquilBooleanKey.AlarmInsulin10.key)
        assertFalse(EquilBooleanKey.AlarmInsulin10.defaultValue)
        assertTrue(EquilBooleanKey.AlarmInsulin10.exportable)
    }

    @Test
    fun `AlarmInsulin5 should have correct properties`() {
        assertEquals("key_equil_alarm_insulin_5", EquilBooleanKey.AlarmInsulin5.key)
        assertFalse(EquilBooleanKey.AlarmInsulin5.defaultValue)
        assertTrue(EquilBooleanKey.AlarmInsulin5.exportable)
    }

    @Test
    fun `all keys should have unique key strings`() {
        val keys = EquilBooleanKey.entries.map { it.key }.toSet()
        assertEquals(4, keys.size, "All key strings should be unique")
    }

    @Test
    fun `all default values should be false`() {
        EquilBooleanKey.entries.forEach { key ->
            assertFalse(key.defaultValue, "${key.name} default value should be false")
        }
    }

    @Test
    fun `all keys should be exportable`() {
        EquilBooleanKey.entries.forEach { key ->
            assertTrue(key.exportable, "${key.name} should be exportable")
        }
    }

    @Test
    fun `enum ordinal values should be stable`() {
        assertEquals(0, EquilBooleanKey.BasalSet.ordinal)
        assertEquals(1, EquilBooleanKey.AlarmBattery10.ordinal)
        assertEquals(2, EquilBooleanKey.AlarmInsulin10.ordinal)
        assertEquals(3, EquilBooleanKey.AlarmInsulin5.ordinal)
    }

    @Test
    fun `key strings should follow naming convention`() {
        EquilBooleanKey.entries.forEach { key ->
            assertTrue(key.key.startsWith("key_equil_"), "${key.name} key should start with 'key_equil_'")
        }
    }

    @Test
    fun `enum should implement BooleanNonPreferenceKey`() {
        val key = EquilBooleanKey.BasalSet
        assert(key is app.aaps.core.keys.interfaces.BooleanNonPreferenceKey)
    }
}
