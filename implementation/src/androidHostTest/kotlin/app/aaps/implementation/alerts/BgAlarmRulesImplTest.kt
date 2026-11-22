package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** The stored BG level rules (`_docs/ALARMS.md`, step 2a): what is written is what is read back. */
class BgAlarmRulesImplTest : TestBase() {

    private val preferences: Preferences = mock()
    private val sut = BgAlarmRulesImpl(preferences, aapsLogger, mock<CoroutineScope>())

    private val night = BgAlarmRule(
        id = "n", direction = BgDirection.LOW, thresholdMgdl = 72.0, severity = AlertSeverity.CRITICAL,
        fromMinute = 22 * 60, toMinute = 7 * 60, durationMinutes = 10, silenceMinutes = 20, enabled = false
    )
    private val high = BgAlarmRule(id = "h", direction = BgDirection.HIGH, thresholdMgdl = 250.0)

    @Test
    fun `the rules are read back as they were written`() {
        assertThat(sut.parse(sut.toJson(listOf(night, high)))).containsExactly(night, high).inOrder()
    }

    /** One rule that cannot be read is dropped, not the whole list. */
    @Test
    fun `an unreadable rule is dropped and the others stay`() {
        val raw = """[{"id":"x","direction":"SIDEWAYS","thresholdMgdl":70.0,"severity":"WARNING"},""" + sut.toJson(listOf(high)).removePrefix("[")

        assertThat(sut.parse(raw)).containsExactly(high)
    }

    @Test
    fun `nothing readable means no rules`() {
        assertThat(sut.parse("not json")).isEmpty()
    }

    /**
     * Saved the same way on every phone (step 2f): on a client the sync sends it to the master, which takes it from an
     * Admin only. Who may edit is the screen's and the master's check, not this store's.
     */
    @Test
    fun `the rules are saved to their synced key`() {
        sut.save(listOf(high))

        verify(preferences).put(eq(StringNonKey.BgAlarmRules), eq(sut.toJson(listOf(high))))
    }
}
