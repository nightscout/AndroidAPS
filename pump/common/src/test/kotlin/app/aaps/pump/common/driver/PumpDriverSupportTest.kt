package app.aaps.pump.common.driver

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.driver.history.PumpHistoryDataProviderAbstract
import app.aaps.pump.common.driver.history.PumpHistoryEntry
import app.aaps.pump.common.driver.history.PumpHistoryPeriod
import app.aaps.pump.common.driver.history.PumpHistoryText
import app.aaps.pump.common.driver.refresh.PumpDataRefreshType
import app.aaps.pump.common.utils.ProfileUtil
import app.aaps.pump.common.utils.and
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import java.util.Calendar
import java.util.GregorianCalendar

internal class PumpDriverSupportTest {

    // ---- BitManipulation ---------------------------------------------------------------------

    // The point of these is masking a signed Byte without sign extension: (-1).toByte() is 0xFF, and
    // a plain toInt() would give -1. Getting this wrong corrupts every parsed pump packet field.
    @Test fun `masking a byte does not carry the sign`() {
        assertThat((0xFF.toByte()) and 0xFF).isEqualTo(255)
        assertThat((0x80.toByte()) and 0xFF).isEqualTo(128)
        assertThat((0x0F.toByte()) and 0xF0).isEqualTo(0)
    }

    @Test fun `masking a short and an int keep their widths`() {
        assertThat((0xFFFF.toShort()) and 0xFFFF).isEqualTo(65535)
        assertThat((-1) and 0xFFFFFFFFL).isEqualTo(4294967295L)
    }

    // ---- PumpCommandType / refresh types -----------------------------------------------------

    @Test fun `every pump command has a description resource`() {
        PumpCommandType.entries.forEach {
            assertThat(it.resourceId).isNotEqualTo(0)
        }
    }

    // A refresh type either maps to the command that fetches it, or is a driver-defined Custom slot
    // with nothing to send. A Custom with a command, or a non-Custom without one, would be a mistake.
    @Test fun `only the custom refresh slots have no command`() {
        val withoutCommand = PumpDataRefreshType.entries.filter { it.commandType == null }

        assertThat(withoutCommand).containsExactly(
            PumpDataRefreshType.Custom_1,
            PumpDataRefreshType.Custom_2,
            PumpDataRefreshType.Custom_3,
            PumpDataRefreshType.Custom_4
        )
    }

    @Test fun `each refresh type asks for the matching command`() {
        assertThat(PumpDataRefreshType.PumpHistory.commandType).isEqualTo(PumpCommandType.GetHistory)
        assertThat(PumpDataRefreshType.RemainingInsulin.commandType).isEqualTo(PumpCommandType.GetRemainingInsulin)
        assertThat(PumpDataRefreshType.BatteryStatus.commandType).isEqualTo(PumpCommandType.GetBatteryStatus)
        assertThat(PumpDataRefreshType.PumpTime.commandType).isEqualTo(PumpCommandType.GetTime)
    }

    // ---- PumpHistoryPeriod -------------------------------------------------------------------

    @Test fun `an untranslated period falls back to its own name`() {
        // Whichever periods this process has already translated, ALL of them must render something
        // usable rather than throwing or showing an empty label.
        PumpHistoryPeriod.entries.forEach {
            assertThat(it.getDisplayValue()).isNotEmpty()
        }
    }

    @Test fun `only the hour based periods are marked as hours`() {
        val hourly = PumpHistoryPeriod.entries.filter { it.isHours }

        assertThat(hourly).containsExactly(
            PumpHistoryPeriod.LAST_HOUR,
            PumpHistoryPeriod.LAST_3_HOURS,
            PumpHistoryPeriod.LAST_6_HOURS,
            PumpHistoryPeriod.LAST_12_HOURS,
            PumpHistoryPeriod.LAST_24_HOURS
        )
    }

    @Test fun `translating the periods gives every one a label`() {
        val rh: ResourceHelper = mock<ResourceHelper>().also {
            whenever(it.gs(any<Int>())).thenAnswer { inv -> "period-${inv.arguments[0]}" }
        }

        PumpHistoryPeriod.doTranslation(rh)

        PumpHistoryPeriod.entries.forEach {
            assertThat(it.getDisplayValue()).isNotEmpty()
        }
    }

    // ---- PumpHistoryDataProviderAbstract -----------------------------------------------------

    private class TestProvider(private val period: PumpHistoryPeriod) : PumpHistoryDataProviderAbstract() {

        var askedFor: PumpHistoryPeriod? = null
        override fun getData(period: PumpHistoryPeriod): List<PumpHistoryEntry> {
            askedFor = period
            return emptyList()
        }

        override fun getInitialPeriod(): PumpHistoryPeriod = period
        override fun getAllowedPumpHistoryGroups(): List<PumpHistoryEntryGroup> = emptyList()
        override fun getText(key: PumpHistoryText): String = "text"
        override fun isItemInSelection(itemGroup: PumpHistoryEntryGroup, targetGroup: PumpHistoryEntryGroup): Boolean = true

        fun startingTime(p: PumpHistoryPeriod) = getStartingTimeForData(p)
    }

    @Test fun `the initial data is fetched for the initial period`() {
        val provider = TestProvider(PumpHistoryPeriod.LAST_6_HOURS)

        provider.getInitialData()

        assertThat(provider.askedFor).isEqualTo(PumpHistoryPeriod.LAST_6_HOURS)
    }

    @Test fun `the spinner has a fixed width`() {
        assertThat(TestProvider(PumpHistoryPeriod.TODAY).getSpinnerWidthInPixels()).isEqualTo(150)
    }

    @Test fun `ALL reaches back to the epoch`() {
        assertThat(TestProvider(PumpHistoryPeriod.ALL).startingTime(PumpHistoryPeriod.ALL)).isEqualTo(0L)
    }

    // A day-based period starts at midnight, an hour-based one at an offset from now. That difference
    // is why `isHours` exists: "last 2 days" means from midnight two days ago, not 48 hours back.
    @Test fun `TODAY starts at midnight`() {
        val start = TestProvider(PumpHistoryPeriod.TODAY).startingTime(PumpHistoryPeriod.TODAY)

        val cal = GregorianCalendar().apply { timeInMillis = start }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
        assertThat(cal.get(Calendar.SECOND)).isEqualTo(0)
    }

    @Test fun `an hour based period is measured back from now, not from midnight`() {
        val provider = TestProvider(PumpHistoryPeriod.LAST_3_HOURS)
        val now = System.currentTimeMillis()

        val start = provider.startingTime(PumpHistoryPeriod.LAST_3_HOURS)

        val threeHours = 3 * 60 * 60 * 1000L
        assertThat(start).isAtMost(now - threeHours + 2000)
        assertThat(start).isAtLeast(now - threeHours - 2000)
    }

    @Test fun `a longer window starts earlier than a shorter one`() {
        val provider = TestProvider(PumpHistoryPeriod.TODAY)

        assertThat(provider.startingTime(PumpHistoryPeriod.LAST_MONTH))
            .isLessThan(provider.startingTime(PumpHistoryPeriod.LAST_WEEK))
        assertThat(provider.startingTime(PumpHistoryPeriod.LAST_WEEK))
            .isLessThan(provider.startingTime(PumpHistoryPeriod.LAST_2_DAYS))
        assertThat(provider.startingTime(PumpHistoryPeriod.LAST_24_HOURS))
            .isLessThan(provider.startingTime(PumpHistoryPeriod.LAST_HOUR))
    }

    // ---- ProfileUtil -------------------------------------------------------------------------

    private fun profileWith(vararg entries: Pair<Int, Double>): Profile =
        mock<Profile>().stub {
            on { getBasalValues() } doReturn entries.map { Profile.ProfileValue(it.first, it.second) }.toTypedArray()
        }

    @Test fun `the displayable profile pairs each hour with its rate`() {
        val profile = profileWith(0 to 0.5, 12 * 3600 to 1.25)

        val text = ProfileUtil.getProfileDisplayable(profile, PumpType.MEDTRONIC_522_722)

        assertThat(text).contains("00:00")
        assertThat(text).contains("12:00")
        // Three decimals, English locale - a comma here would be read as a separator by the caller.
        assertThat(text).contains("0.500")
        assertThat(text).doesNotContain("0,500")
    }

    @Test fun `the displayable profile has no trailing separator`() {
        val text = ProfileUtil.getProfileDisplayable(profileWith(0 to 1.0), PumpType.MEDTRONIC_522_722)

        assertThat(text).doesNotMatch(".*, $")
    }

    @Test fun `an empty profile gives an empty string rather than a stray separator`() {
        assertThat(ProfileUtil.getProfileDisplayable(profileWith(), PumpType.MEDTRONIC_522_722)).isEmpty()
    }

    // The array form repeats each rate once per hour it covers, space separated, and the last entry
    // runs to 24:00 - so a two-entry profile split at noon yields twelve of each.
    @Test fun `the array form expands every hour of the day`() {
        val profile = profileWith(0 to 0.5, 12 * 3600 to 1.0)

        val values = ProfileUtil.getBasalProfilesDisplayableAsStringOfArray(profile, PumpType.MEDTRONIC_522_722)
            .split(" ").filter { it.isNotBlank() }

        assertThat(values).hasSize(24)
        assertThat(values.count { it == "0.500" }).isEqualTo(12)
        assertThat(values.count { it == "1.000" }).isEqualTo(12)
    }

    @Test fun `a single entry profile covers the whole day at one rate`() {
        val values = ProfileUtil.getBasalProfilesDisplayableAsStringOfArray(profileWith(0 to 0.8), PumpType.MEDTRONIC_522_722)
            .split(" ").filter { it.isNotBlank() }

        assertThat(values).hasSize(24)
        assertThat(values.toSet()).containsExactly("0.800")
    }
}
