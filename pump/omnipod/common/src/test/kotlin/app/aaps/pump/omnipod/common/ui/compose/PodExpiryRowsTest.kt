package app.aaps.pump.omnipod.common.ui.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.omnipod.common.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PodExpiryRowsTest {

    private val hour = 1.hours.inWholeMilliseconds
    private val minute = 1.minutes.inWholeMilliseconds

    // Pod life of 72 h plus 8 h of grace period, counted from activation at 0
    private val expiresAt = 72 * hour
    private val hardEndAt = 80 * hour

    private val rh: ResourceHelper = mock()
    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setUp() {
        whenever(rh.gs(R.string.omnipod_common_overview_pod_expiry_date)).thenReturn("Pod Expires")
        whenever(rh.gs(R.string.omnipod_common_overview_pod_hard_end_date)).thenReturn("Pod Hard End")
        whenever(rh.gs(eq(R.string.omnipod_common_overview_pod_hard_end_value), anyVararg<Any>()))
            .thenAnswer { "${it.getArgument<Any>(1)} (${it.getArgument<Any>(2)})" }
        whenever(dateUtil.timeRemainingString(any(), any())).thenAnswer { "${it.getArgument<Long>(0) / minute}m remaining" }
    }

    private fun rows(now: Long, expiresAt: Long? = this.expiresAt, hardEndAt: Long? = this.hardEndAt): Pair<PumpInfoRow, PumpInfoRow> {
        val rows = podExpiryRows(rh, dateUtil, now, expiresAt, hardEndAt) { "${it / hour}h" }
        assertThat(rows).hasSize(2)
        return rows[0] to rows[1]
    }

    private fun levels(now: Long): Pair<StatusLevel, StatusLevel> = rows(now).let { (expiry, hardEnd) -> expiry.level to hardEnd.level }

    @Test
    fun beforeTheReminder_bothNormal() {
        assertThat(levels(expiresAt - 4 * hour - 1)).isEqualTo(StatusLevel.NORMAL to StatusLevel.NORMAL)
    }

    @Test
    fun lastFourHoursBeforeExpiry_onlyExpiryWarns() {
        assertThat(levels(expiresAt - 4 * hour)).isEqualTo(StatusLevel.WARNING to StatusLevel.NORMAL)
        assertThat(levels(expiresAt - 1)).isEqualTo(StatusLevel.WARNING to StatusLevel.NORMAL)
    }

    @Test
    fun firstHalfOfGracePeriod_bothWarn() {
        assertThat(levels(expiresAt)).isEqualTo(StatusLevel.WARNING to StatusLevel.WARNING)
        assertThat(levels(hardEndAt - 4 * hour - 1)).isEqualTo(StatusLevel.WARNING to StatusLevel.WARNING)
    }

    @Test
    fun lastFourHoursBeforeHardEnd_hardEndIsCritical_expiryIsNeverCritical() {
        assertThat(levels(hardEndAt - 4 * hour)).isEqualTo(StatusLevel.WARNING to StatusLevel.CRITICAL)
        assertThat(levels(hardEndAt + 10 * hour)).isEqualTo(StatusLevel.WARNING to StatusLevel.CRITICAL)
    }

    /** The case from issue #5365: 17 minutes before the pod stops. */
    @Test
    fun shortlyBeforeHardEnd_showsTimeLeft() {
        val (expiry, hardEnd) = rows(hardEndAt - 17 * minute)

        assertThat(expiry.label).isEqualTo("Pod Expires")
        assertThat(expiry.value).isEqualTo("72h")
        assertThat(expiry.level).isEqualTo(StatusLevel.WARNING)
        assertThat(hardEnd.label).isEqualTo("Pod Hard End")
        assertThat(hardEnd.value).isEqualTo("80h (17m remaining)")
        assertThat(hardEnd.level).isEqualTo(StatusLevel.CRITICAL)
    }

    @Test
    fun timeLeft_onlyDuringGracePeriod() {
        assertThat(rows(expiresAt - 1).second.value).isEqualTo("80h")
        assertThat(rows(expiresAt).second.value).isEqualTo("80h (480m remaining)")
        assertThat(rows(hardEndAt).second.value).isEqualTo("80h")
    }

    @Test
    fun unknownTimes_showPlaceholdersInNormalColour() {
        val (expiry, hardEnd) = rows(now = 100 * hour, expiresAt = null, hardEndAt = null)

        assertThat(expiry.value).isEqualTo("-")
        assertThat(expiry.level).isEqualTo(StatusLevel.NORMAL)
        assertThat(hardEnd.value).isEqualTo("-")
        assertThat(hardEnd.level).isEqualTo(StatusLevel.NORMAL)
    }
}
