package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.plugins.constraints.dstHelper.keys.DstHelperLongKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Instant

/** Rome changed to summer time on 2018-03-25 at 01:00 UTC and back on 2018-10-28 at 01:00 UTC. */
class DstHelperPluginTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop

    private lateinit var plugin: DstHelperPlugin
    private lateinit var savedZone: java.util.TimeZone
    private val rome = TimeZone.of("Europe/Rome")

    @BeforeEach
    fun mock() {
        savedZone = java.util.TimeZone.getDefault()
        plugin = DstHelperPlugin(aapsLogger, rh, preferences, activePlugin, notificationManager, loop, profileFunction, dateUtil)
    }

    @AfterEach
    fun restoreZone() {
        java.util.TimeZone.setDefault(savedZone)
    }

    private fun at(utc: String): Long = Instant.parse(utc).toEpochMilliseconds()

    // ---- wasDST / willBeDST -------------------------------------------------------------------------

    @Test
    fun `spring change in 5 minutes warns`() {
        assertThat(plugin.wasDST(at("2018-03-25T00:55:00Z"), rome)).isFalse()
        assertThat(plugin.willBeDST(at("2018-03-25T00:55:00Z"), rome)).isTrue()
    }

    @Test
    fun `spring change 5 minutes ago suspends`() {
        assertThat(plugin.wasDST(at("2018-03-25T01:05:00Z"), rome)).isTrue()
        assertThat(plugin.willBeDST(at("2018-03-25T01:05:00Z"), rome)).isFalse()
    }

    @Test
    fun `spring change 2 hours 55 minutes ago still suspends`() {
        assertThat(plugin.wasDST(at("2018-03-25T03:55:00Z"), rome)).isTrue()
        assertThat(plugin.willBeDST(at("2018-03-25T03:55:00Z"), rome)).isFalse()
    }

    @Test
    fun `spring change 3 hours 5 minutes ago is over`() {
        assertThat(plugin.wasDST(at("2018-03-25T04:05:00Z"), rome)).isFalse()
        assertThat(plugin.willBeDST(at("2018-03-25T04:05:00Z"), rome)).isFalse()
    }

    @Test
    fun `autumn change in 11 hours warns`() {
        assertThat(plugin.willBeDST(at("2018-10-27T14:00:00Z"), rome)).isTrue()
        assertThat(plugin.wasDST(at("2018-10-27T14:00:00Z"), rome)).isFalse()
    }

    @Test
    fun `autumn change 2 hours ago suspends`() {
        assertThat(plugin.wasDST(at("2018-10-28T03:00:00Z"), rome)).isTrue()
        assertThat(plugin.willBeDST(at("2018-10-28T03:00:00Z"), rome)).isFalse()
    }

    @Test
    fun `autumn change 4 hours ago is over`() {
        assertThat(plugin.wasDST(at("2018-10-28T05:00:00Z"), rome)).isFalse()
        assertThat(plugin.willBeDST(at("2018-10-28T05:00:00Z"), rome)).isFalse()
    }

    @Test
    fun `a zone without summer time never changes`() {
        val kolkata = TimeZone.of("Asia/Kolkata")
        assertThat(plugin.wasDST(at("2018-03-25T01:00:00Z"), kolkata)).isFalse()
        assertThat(plugin.willBeDST(at("2018-03-25T01:00:00Z"), kolkata)).isFalse()
    }

    // ---- dstCheck -----------------------------------------------------------------------------------

    /** A pump that cannot handle a clock change, a phone in Rome, and the clock at [utc]. */
    private fun phoneInRomeAt(utc: String, canHandleDst: Boolean = false) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Rome"))
        val pump = mock<PumpWithConcentration>()
        whenever(pump.canHandleDST()).thenReturn(canHandleDst)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(dateUtil.now()).thenReturn(at(utc))
    }

    private fun postedNotifications(): List<NotificationId> =
        mockingDetails(notificationManager).invocations.filter { it.method.name == "post" }.map { it.arguments[0] as NotificationId }

    @Test
    fun `dstCheck does nothing for a pump that handles the clock change`() = runTest {
        phoneInRomeAt("2018-10-28T03:00:00Z", canHandleDst = true)

        plugin.dstCheck()

        assertThat(postedNotifications()).isEmpty()
        verify(loop, never()).runningMode()
    }

    @Test
    fun `dstCheck warns before the change`() = runTest {
        phoneInRomeAt("2018-10-27T14:00:00Z")

        plugin.dstCheck()

        assertThat(postedNotifications()).containsExactly(NotificationId.DST_IN_24H)
    }

    @Test
    fun `dstCheck does not warn while the warning is snoozed`() = runTest {
        phoneInRomeAt("2018-10-27T14:00:00Z")
        whenever(preferences.get(DstHelperLongKey.SnoozeDstIn24h)).thenReturn(at("2018-10-27T20:00:00Z"))

        plugin.dstCheck()

        assertThat(postedNotifications()).isEmpty()
    }

    @Test
    fun `dstCheck suspends the loop for 3 hours after the change`() = runTest {
        phoneInRomeAt("2018-10-28T03:00:00Z")
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)

        plugin.dstCheck()

        verify(loop).handleRunningModeChange(eq(RM.Mode.SUSPENDED_BY_DST), eq(Action.SUSPEND), eq(Sources.Aaps), any(), eq(180), eq(effectiveProfile))
        assertThat(postedNotifications()).containsExactly(NotificationId.DST_LOOP_DISABLED)
    }

    @Test
    fun `dstCheck leaves a loop that is already paused`() = runTest {
        phoneInRomeAt("2018-10-28T03:00:00Z")
        whenever(loop.runningMode()).thenReturn(RM.Mode.DISCONNECTED_PUMP)

        plugin.dstCheck()

        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
        assertThat(postedNotifications()).isEmpty()
    }
}
