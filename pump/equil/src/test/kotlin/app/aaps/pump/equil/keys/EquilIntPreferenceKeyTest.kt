package app.aaps.pump.equil.keys

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquilIntPreferenceKeyTest : TestBase() {

    @Test
    fun `all enum values should be accessible`() {
        val allKeys = EquilIntPreferenceKey.entries
        assertEquals(2, allKeys.size)
        assert(allKeys.contains(EquilIntPreferenceKey.EquilTone))
        assert(allKeys.contains(EquilIntPreferenceKey.EquilBolusSpeed))
    }

    @Test
    fun `bolus speed defaults to the standard 1_5 U per min and offers only tested speeds`() {
        val speed = EquilIntPreferenceKey.EquilBolusSpeed
        assertEquals("key_equil_bolus_speed", speed.key)
        // Seconds per unit. 40 = 1.5 U/min, the speed AAPS always used.
        assertEquals(40, speed.defaultValue)
        assertEquals(setOf(40, 20, 10, 5), speed.entries.keys)
        // 5 s/U = 12 U/min is the fastest tested on a real pump; 30 U/min was refused.
        assertEquals(5, speed.min)
        assertEquals(40, speed.max)
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(EquilIntPreferenceKey.EquilTone, EquilIntPreferenceKey.valueOf("EquilTone"))
    }

    @Test
    fun `EquilTone should have correct key`() {
        assertEquals("key_equil_tone", EquilIntPreferenceKey.EquilTone.key)
    }

    @Test
    fun `EquilTone should have correct default value`() {
        assertEquals(3, EquilIntPreferenceKey.EquilTone.defaultValue)
    }

    @Test
    fun `EquilTone should have correct min value`() {
        assertEquals(0, EquilIntPreferenceKey.EquilTone.min)
    }

    @Test
    fun `EquilTone should have correct max value`() {
        assertEquals(3, EquilIntPreferenceKey.EquilTone.max)
    }

    @Test
    fun `EquilTone should have correct calculatedDefaultValue`() {
        assertFalse(EquilIntPreferenceKey.EquilTone.calculatedDefaultValue)
    }

    @Test
    fun `EquilTone should have correct engineeringModeOnly`() {
        assertFalse(EquilIntPreferenceKey.EquilTone.engineeringModeOnly)
    }

    @Test
    fun `EquilTone should have correct defaultedBySM`() {
        assertFalse(EquilIntPreferenceKey.EquilTone.defaultedBySM)
    }

    @Test
    fun `EquilTone should be shown in all modes`() {
        assertTrue(EquilIntPreferenceKey.EquilTone.showInApsMode)
        assertTrue(EquilIntPreferenceKey.EquilTone.showInNsClientMode)
        assertTrue(EquilIntPreferenceKey.EquilTone.showInPumpControlMode)
    }

    @Test
    fun `EquilTone should have no dependencies`() {
        assertNull(EquilIntPreferenceKey.EquilTone.dependency)
        assertNull(EquilIntPreferenceKey.EquilTone.negativeDependency)
    }

    @Test
    fun `EquilTone should not hide parent screen`() {
        assertFalse(EquilIntPreferenceKey.EquilTone.hideParentScreenIfHidden)
    }

    @Test
    fun `EquilTone should be exportable`() {
        assertTrue(EquilIntPreferenceKey.EquilTone.exportable)
    }

    @Test
    fun `EquilTone default value should be within min max range`() {
        val tone = EquilIntPreferenceKey.EquilTone
        assertTrue(tone.defaultValue >= tone.min, "Default value should be >= min")
        assertTrue(tone.defaultValue <= tone.max, "Default value should be <= max")
    }

    @Test
    fun `EquilTone min should be less than or equal to max`() {
        val tone = EquilIntPreferenceKey.EquilTone
        assertTrue(tone.min <= tone.max, "Min should be <= max")
    }

    @Test
    fun `enum ordinal value should be stable`() {
        assertEquals(0, EquilIntPreferenceKey.EquilTone.ordinal)
    }

    @Test
    fun `key string should follow naming convention`() {
        assertTrue(
            EquilIntPreferenceKey.EquilTone.key.startsWith("key_equil_"),
            "Key should start with 'key_equil_'"
        )
    }
    @Test
    fun `EquilTone range should be valid for alarm mode values`() {
        // Based on AlarmMode enum (MUTE=0, TONE=1, SHAKE=2, TONE_AND_SHAKE=3)
        val tone = EquilIntPreferenceKey.EquilTone
        assertEquals(0, tone.min, "Min should match AlarmMode.MUTE")
        assertEquals(3, tone.max, "Max should match AlarmMode.TONE_AND_SHAKE")
    }
}
