package app.aaps.pump.equil.data

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlarmModeTest : TestBase() {

    @Test
    fun `all modes should have correct command values`() {
        assertEquals(0, AlarmMode.MUTE.command)
        assertEquals(1, AlarmMode.TONE.command)
        assertEquals(2, AlarmMode.SHAKE.command)
        assertEquals(3, AlarmMode.TONE_AND_SHAKE.command)
    }

    @Test
    fun `fromInt should return correct mode for valid values`() {
        assertEquals(AlarmMode.MUTE, AlarmMode.fromInt(0))
        assertEquals(AlarmMode.TONE, AlarmMode.fromInt(1))
        assertEquals(AlarmMode.SHAKE, AlarmMode.fromInt(2))
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.fromInt(3))
    }

    @Test
    fun `fromInt should return TONE_AND_SHAKE for invalid values`() {
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.fromInt(-1))
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.fromInt(4))
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.fromInt(100))
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.fromInt(Integer.MAX_VALUE))
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.fromInt(Integer.MIN_VALUE))
    }

    @Test
    fun `all enum values should be accessible`() {
        val allModes = AlarmMode.entries
        assertEquals(4, allModes.size)
        assert(allModes.contains(AlarmMode.MUTE))
        assert(allModes.contains(AlarmMode.TONE))
        assert(allModes.contains(AlarmMode.SHAKE))
        assert(allModes.contains(AlarmMode.TONE_AND_SHAKE))
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(AlarmMode.MUTE, AlarmMode.valueOf("MUTE"))
        assertEquals(AlarmMode.TONE, AlarmMode.valueOf("TONE"))
        assertEquals(AlarmMode.SHAKE, AlarmMode.valueOf("SHAKE"))
        assertEquals(AlarmMode.TONE_AND_SHAKE, AlarmMode.valueOf("TONE_AND_SHAKE"))
    }

    @Test
    fun `fromInt roundtrip should work correctly`() {
        AlarmMode.entries.forEach { mode ->
            assertEquals(mode, AlarmMode.fromInt(mode.command))
        }
    }

    @Test
    fun `enum toString should return name`() {
        assertEquals("MUTE", AlarmMode.MUTE.toString())
        assertEquals("TONE", AlarmMode.TONE.toString())
        assertEquals("SHAKE", AlarmMode.SHAKE.toString())
        assertEquals("TONE_AND_SHAKE", AlarmMode.TONE_AND_SHAKE.toString())
    }

    @Test
    fun `command values should be sequential`() {
        assertEquals(0, AlarmMode.MUTE.command)
        assertEquals(1, AlarmMode.TONE.command)
        assertEquals(2, AlarmMode.SHAKE.command)
        assertEquals(3, AlarmMode.TONE_AND_SHAKE.command)
    }

    @Test
    fun `enum ordinal values should be stable`() {
        assertEquals(0, AlarmMode.MUTE.ordinal)
        assertEquals(1, AlarmMode.TONE.ordinal)
        assertEquals(2, AlarmMode.SHAKE.ordinal)
        assertEquals(3, AlarmMode.TONE_AND_SHAKE.ordinal)
    }
}
