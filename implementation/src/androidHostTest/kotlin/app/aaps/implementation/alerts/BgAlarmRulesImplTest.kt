package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
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
        fromMinute = 22 * 60, toMinute = 7 * 60, enabled = false
    )
    private val high = BgAlarmRule(id = "h", direction = BgDirection.HIGH, thresholdMgdl = 250.0)
    private val persistent = BgAlarmRule(id = "p", type = BgRuleType.PERSISTENT_HIGH, direction = BgDirection.HIGH, thresholdMgdl = 180.0, durationMinutes = 45)

    @Test
    fun `the rules are read back as they were written`() {
        assertThat(sut.parse(sut.toJson(listOf(night, high, persistent)))).containsExactly(night, high, persistent).inOrder()
    }

    /**
     * A level rule has no delay any more (decided 2026-11-27). A high stored with one meant "only if it stays high":
     * it is read as persistent high, so it does not ring after every meal from now on.
     */
    @Test
    fun `a high stored with a delay is read as persistent high`() {
        val stored = BgAlarmRule(id = "h", direction = BgDirection.HIGH, thresholdMgdl = 200.0, severity = AlertSeverity.CRITICAL, fromMinute = 60, toMinute = 120, durationMinutes = 30)

        assertThat(sut.parse(sut.toJson(listOf(stored)))).containsExactly(stored.copy(type = BgRuleType.PERSISTENT_HIGH))
    }

    @Test
    fun `a high stored with a short delay waits as long as persistent high allows`() {
        val stored = BgAlarmRule(id = "h", direction = BgDirection.HIGH, thresholdMgdl = 200.0, durationMinutes = 5)

        assertThat(sut.parse(sut.toJson(listOf(stored))).single().durationMinutes).isEqualTo(BgAlarmRule.PERSISTENT_HIGH_MIN_MINUTES)
    }

    /** Waiting is the worst thing to do with a low: a low stored with a delay rings at once. */
    @Test
    fun `a low stored with a delay is read with none`() {
        val stored = BgAlarmRule(id = "l", direction = BgDirection.LOW, thresholdMgdl = 72.0, durationMinutes = 10)

        assertThat(sut.parse(sut.toJson(listOf(stored)))).containsExactly(stored.copy(durationMinutes = 0))
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

    /** A phone that never stored rules has the default ones (decided 2026-11-26), the same on every phone. */
    @Test
    fun `never stored means the default rules`() {
        assertThat(sut.parse("")).isEqualTo(BgAlarmRules.DEFAULT)
    }

    /** A list the user emptied is theirs: it is not filled again. */
    @Test
    fun `an emptied list stays empty`() {
        assertThat(sut.parse(sut.toJson(emptyList()))).isEmpty()
    }

    /** "Reset rules to default" stores them, so they sync as any change does. */
    @Test
    fun `the default rules are stored and read back as they are`() {
        assertThat(sut.parse(sut.toJson(BgAlarmRules.DEFAULT))).isEqualTo(BgAlarmRules.DEFAULT)
    }

    /** The ids are stored with the user's silences and edits: pinned, so a rename is not quiet. */
    @Test
    fun `the default rules keep their ids`() {
        assertThat(BgAlarmRules.DEFAULT.map { it.id }).containsExactly("default.low", "default.high", "default.predicted_low").inOrder()
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
