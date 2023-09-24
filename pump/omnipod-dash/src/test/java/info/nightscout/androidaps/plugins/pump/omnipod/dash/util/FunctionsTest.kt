package info.nightscout.androidaps.plugins.pump.omnipod.dash.util

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertFailsWith

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
        Assertions.assertEquals(3, entries.size)
        val entry1: BasalProgram.Segment = entries[0]
        Assertions.assertEquals(0.toShort(), entry1.startSlotIndex)
        Assertions.assertEquals(50, entry1.basalRateInHundredthUnitsPerHour)
        Assertions.assertEquals(10.toShort(), entry1.endSlotIndex)
        val entry2: BasalProgram.Segment = entries[1]
        Assertions.assertEquals(10.toShort(), entry2.startSlotIndex)
        Assertions.assertEquals(100, entry2.basalRateInHundredthUnitsPerHour)
        Assertions.assertEquals(28.toShort(), entry2.endSlotIndex)
        val entry3: BasalProgram.Segment = entries[2]
        Assertions.assertEquals(28.toShort(), entry3.startSlotIndex)
        Assertions.assertEquals(305, entry3.basalRateInHundredthUnitsPerHour)
        Assertions.assertEquals(48.toShort(), entry3.endSlotIndex)
    }

    @Test fun invalidProfileZeroEntries() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(emptyArray())

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("Basal values should contain values")
    }

    @Test fun invalidProfileNonZeroOffset() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(ProfileValue(1800, 0.5))
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("First basal segment start time should be 0")
    }

    @Test fun invalidProfileMoreThan24Hours() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(86400, 0.5)
            )
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("Basal segment start time can not be greater than 86400")
    }

    @Test fun invalidProfileNegativeOffset() {
        val profile = Mockito.mock(Profile::class.java)

        `when`(profile.getBasalValues()).thenReturn(
            arrayOf(ProfileValue(-1, 0.5))
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("Basal segment start time can not be less than 0")
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
        Assertions.assertEquals(5, basalProgramElement.basalRateInHundredthUnitsPerHour)
    }
}
