package info.nightscout.androidaps.plugins.pump.omnipod.dash.util

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.Profile.ProfileValue
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class FunctionsTest {

    @Test fun validProfile() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(18000, 1.0),
                ProfileValue(50400, 3.05)
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
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(emptyArray())

        Assert.assertThrows(
            "Basal values should contain values",
            java.lang.IllegalArgumentException::class.java
        ) {
            mapProfileToBasalProgram(profile)
        }
    }

    @Test fun invalidProfileNonZeroOffset() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(ProfileValue(1800, 0.5))
        )

        Assert.assertThrows(
            "First basal segment start time should be 0",
            java.lang.IllegalArgumentException::class.java
        ) {
            mapProfileToBasalProgram(profile)
        }
    }

    @Test fun invalidProfileMoreThan24Hours() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(86400, 0.5)
            )
        )

        Assert.assertThrows(
            "Basal segment start time can not be greater than 86400",
            java.lang.IllegalArgumentException::class.java
        ) {
            mapProfileToBasalProgram(profile)
        }
    }

    @Test fun invalidProfileNegativeOffset() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(ProfileValue(-1, 0.5))
        )

        Assert.assertThrows("Basal segment start time can not be less than 0", IllegalArgumentException::class.java) {
            mapProfileToBasalProgram(profile)
        }
    }

    @Test fun roundsToSupportedPrecision() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.04)
            )
        )

        val basalProgram: BasalProgram = mapProfileToBasalProgram(profile)
        val basalProgramElement: BasalProgram.Segment = basalProgram.segments[0]
        assertEquals(5, basalProgramElement.basalRateInHundredthUnitsPerHour)
    }
}
