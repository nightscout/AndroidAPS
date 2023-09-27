package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import org.joda.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertFailsWith

internal class AapsOmnipodErosManagerTest {

    @Test fun validProfile() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(18000, 1.0),
                ProfileValue(50400, 3.05)
            )
        )
        val basalSchedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile)
        val entries = basalSchedule.entries
        Assertions.assertEquals(3, entries.size)
        val entry1 = entries[0]
        Assertions.assertEquals(Duration.standardSeconds(0), entry1.startTime)
        Assertions.assertEquals(0.5, entry1.rate, 0.000001)
        val entry2 = entries[1]
        Assertions.assertEquals(Duration.standardSeconds(18000), entry2.startTime)
        Assertions.assertEquals(1.0, entry2.rate, 0.000001)
        val entry3 = entries[2]
        Assertions.assertEquals(Duration.standardSeconds(50400), entry3.startTime)
        Assertions.assertEquals(3.05, entry3.rate, 0.000001)
    }

    @Test fun invalidProfileNullProfile() {
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(null) }
    }

    @Test fun invalidProfileNullEntries() {
        assertFailsWith<IllegalArgumentException> {
            AapsOmnipodErosManager.mapProfileToBasalSchedule(Mockito.mock(Profile::class.java))
        }
    }

    @Test fun invalidProfileZeroEntries() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(emptyArray())
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun invalidProfileNonZeroOffset() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(1800, 0.5)
            )
        )
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun invalidProfileMoreThan24Hours() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.5),
                ProfileValue(86400, 0.5)
            )
        )
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun invalidProfileNegativeOffset() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(-1, 0.5)
            )
        )
        assertFailsWith<IllegalArgumentException> { AapsOmnipodErosManager.mapProfileToBasalSchedule(profile) }
    }

    @Test fun roundsToSupportedPrecision() {
        val profile = Mockito.mock(Profile::class.java)
        Mockito.`when`(profile.getBasalValues()).thenReturn(
            arrayOf(
                ProfileValue(0, 0.04)
            )
        )
        val basalSchedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile)
        val basalScheduleEntry = basalSchedule.entries[0]
        Assertions.assertEquals(0.05, basalScheduleEntry.rate, 0.000001)
    }
}
