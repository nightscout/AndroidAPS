package app.aaps.pump.omnipod.dash.util

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

class FunctionsTest {

    @Test fun validProfile() {
        val profile: Profile = mock()

        whenever(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(18000, 1.0),
                ProfileValue(50400, 3.05)
            )
        )

        val basalProgram: BasalProgram = mapProfileToBasalProgram(profile)
        val entries: List<BasalProgram.Segment> = basalProgram.segments
        assertThat(entries).hasSize(3)
        val entry1: BasalProgram.Segment = entries[0]
        assertThat(entry1.startSlotIndex).isEqualTo(0.toShort())
        assertThat(entry1.basalRateInHundredthUnitsPerHour).isEqualTo(50)
        assertThat(entry1.endSlotIndex).isEqualTo(10.toShort())
        val entry2: BasalProgram.Segment = entries[1]
        assertThat(entry2.startSlotIndex).isEqualTo(10.toShort())
        assertThat(entry2.basalRateInHundredthUnitsPerHour).isEqualTo(100)
        assertThat(entry2.endSlotIndex).isEqualTo(28.toShort())
        val entry3: BasalProgram.Segment = entries[2]
        assertThat(entry3.startSlotIndex).isEqualTo(28.toShort())
        assertThat(entry3.basalRateInHundredthUnitsPerHour).isEqualTo(305)
        assertThat(entry3.endSlotIndex).isEqualTo(48.toShort())
    }

    @Test fun invalidProfileZeroEntries() {
        val profile: Profile = mock()

        whenever(profile.getBasalValues()).thenReturn(emptyArray())

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("Basal values should contain values")
    }

    @Test fun invalidProfileNonZeroOffset() {
        val profile: Profile = mock()

        whenever(profile.getBasalValues()).thenReturn(
            arrayOf(ProfileValue(1800, 0.5))
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("First basal segment start time should be 0")
    }

    @Test fun invalidProfileMoreThan24Hours() {
        val profile: Profile = mock()

        whenever(profile.getBasalValues()).thenReturn(
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
        val profile: Profile = mock()

        whenever(profile.getBasalValues()).thenReturn(
            arrayOf(ProfileValue(-1, 0.5))
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            mapProfileToBasalProgram(profile)
        }
        assertThat(exception.message).isEqualTo("Basal segment start time can not be less than 0")
    }

    @Test fun roundsToSupportedPrecision() {
        val profile: Profile = mock()

        whenever(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.04)
            )
        )

        val basalProgram: BasalProgram = mapProfileToBasalProgram(profile)
        val basalProgramElement: BasalProgram.Segment = basalProgram.segments[0]
        assertThat(basalProgramElement.basalRateInHundredthUnitsPerHour).isEqualTo(5)
    }
}
