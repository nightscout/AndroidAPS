package info.nightscout.androidaps.plugins.pump.omnipod.dash.util

import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.Profile.ProfileValue
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito

class FunctionsTest {

    @Rule
    @JvmField var thrown = ExpectedException.none()

    @Test fun validProfile() {
        val profile = Mockito.mock(Profile::class.java)
        val value1 = Mockito.mock(ProfileValue::class.java)
        value1.timeAsSeconds = 0
        value1.value = 0.5
        val value2 = Mockito.mock(ProfileValue::class.java)
        value2.timeAsSeconds = 18000
        value2.value = 1.0
        val value3 = Mockito.mock(ProfileValue::class.java)
        value3.timeAsSeconds = 50400
        value3.value = 3.05
        PowerMockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                value1,
                value2,
                value3
            )
        )
        val basalProgram: BasalProgram = mapProfileToBasalProgram(profile)
        val entries: List<BasalProgram.Segment> = basalProgram.segments
        assertEquals(3, entries.size)
        val entry1: BasalProgram.Segment = entries[0]
        assertEquals(0.toShort(), entry1.startSlotIndex)
        assertEquals(50, entry1.basalRateInHundredthUnitsPerHour)
        assertEquals(10.toShort(), entry1.endSlotIndex)
        val entry2: BasalProgram.Segment = entries[1]
        assertEquals(10.toShort(), entry2.startSlotIndex)
        assertEquals(100, entry2.basalRateInHundredthUnitsPerHour)
        assertEquals(28.toShort(), entry2.endSlotIndex)
        val entry3: BasalProgram.Segment = entries[2]
        assertEquals(28.toShort(), entry3.startSlotIndex)
        assertEquals(305, entry3.basalRateInHundredthUnitsPerHour)
        assertEquals(48.toShort(), entry3.endSlotIndex)
    }

    @Test fun invalidProfileZeroEntries() {
        thrown.expect(IllegalArgumentException::class.java)
        thrown.expectMessage("Basal values should contain values")
        val profile = Mockito.mock(Profile::class.java)
        PowerMockito.`when`(profile.getBasalValues()).thenReturn(emptyArray())
        mapProfileToBasalProgram(profile)
    }

    @Test fun invalidProfileNonZeroOffset() {
        thrown.expect(IllegalArgumentException::class.java)
        thrown.expectMessage("First basal segment start time should be 0")
        val profile = Mockito.mock(Profile::class.java)
        val value = Mockito.mock(ProfileValue::class.java)
        value.timeAsSeconds = 1800
        value.value = 0.5
        PowerMockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                value
            )
        )
        mapProfileToBasalProgram(profile)
    }

    @Test fun invalidProfileMoreThan24Hours() {
        thrown.expect(IllegalArgumentException::class.java)
        thrown.expectMessage("Basal segment start time can not be greater than 86400")

        val profile = Mockito.mock(Profile::class.java)
        val value1 = Mockito.mock(ProfileValue::class.java)
        value1.timeAsSeconds = 0
        value1.value = 0.5
        val value2 = Mockito.mock(ProfileValue::class.java)
        value2.timeAsSeconds = 86400
        value2.value = 0.5
        PowerMockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                value1,
                value2
            )
        )
        mapProfileToBasalProgram(profile)
    }

    @Test fun invalidProfileNegativeOffset() {
        thrown.expect(IllegalArgumentException::class.java)
        thrown.expectMessage("Basal segment start time can not be less than 0")
        val profile = Mockito.mock(Profile::class.java)
        val value = Mockito.mock(ProfileValue::class.java)
        value.timeAsSeconds = -1
        value.value = 0.5
        PowerMockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                value
            )
        )
        mapProfileToBasalProgram(profile)
    }

    @Test fun roundsToSupportedPrecision() {
        val profile = Mockito.mock(Profile::class.java)
        val value = Mockito.mock(ProfileValue::class.java)
        value.timeAsSeconds = 0
        value.value = 0.04
        PowerMockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                value
            )
        )
        val basalProgram: BasalProgram = mapProfileToBasalProgram(profile)
        val basalProgramElement: BasalProgram.Segment = basalProgram.segments[0]
        assertEquals(5, basalProgramElement.basalRateInHundredthUnitsPerHour)
    }
}
