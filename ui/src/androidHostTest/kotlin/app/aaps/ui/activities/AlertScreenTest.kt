package app.aaps.ui.activities

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * Robolectric composable test for [AlertScreen] with [ErrorScreen]: what each button of the alarm screen does (decided
 * 2026-11-26). An alert that lasts is silenced in one tap per length, and the tap closes the screen; a BG alarm adds
 * "On every phone" and "Silence all BG alarms"; an alarm that ends when it is answered keeps Mute 5 min and Silence and
 * close. That the card then stays without sound is the core's, in `AlertsImplTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AlertScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val alerts: Alerts = mock()
    private val uel: UserEntryLogger = mock()
    private var closed = 0

    /** Its own, not left by another test: without it the `UiStrings` show as their key names. */
    @Before
    fun registerUiStrings() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    private fun alert(kind: AlertKind, lifetime: AlertLifetime = AlertLifetime.CONDITION) =
        Alert(kind = kind, key = KEY, severity = AlertSeverity.CRITICAL, title = TextRef.Literal("Alarm"), raisedAt = 1L, lifetime = lifetime)

    private fun show(alert: Alert, everywhere: Boolean = false) {
        whenever(alerts.active).thenReturn(MutableStateFlow(listOf(alert)))
        whenever(alerts.canSilenceEverywhere(any())).thenReturn(everywhere)
        compose.setContent { MaterialTheme { AlertScreen(alerts, alert.source, KEY, uel, onClose = { closed++ }) } }
    }

    private fun minutes(m: Int) = ctx.getString(CoreUiR.string.format_mins, m)
    private val oneHour get() = ctx.getString(CoreUiR.string.format_hours_only, 1)
    private val silenceAll get() = ctx.getString(R.string.alarm_silence_all_bg, oneHour)

    @Test
    fun `one tap on a length silences a lasting alert for that long and closes the screen`() {
        show(alert(BgAlert.HIGH))

        compose.onNodeWithText(ctx.getString(CoreUiR.string.alarm_silence_for_label)).assertIsDisplayed()
        compose.onNodeWithText(minutes(30)).performClick()

        verify(alerts).silence(BgAlert.SOURCE, KEY, 30)
        verify(alerts, never()).silenceEverywhere(any(), any(), any())
        assertThat(closed).isEqualTo(1)
    }

    /** The core never silences a protected kind for longer than 30 min, so the screen does not offer it. */
    @Test
    fun `a protected kind offers 15 and 30 min and nothing that silences more`() {
        show(alert(BgAlert.URGENT_LOW))

        compose.onNodeWithText(minutes(15)).assertIsDisplayed()
        compose.onNodeWithText(minutes(30)).assertIsDisplayed()
        compose.onNodeWithText(oneHour).assertDoesNotExist()
        compose.onNodeWithText(silenceAll).assertDoesNotExist()
    }

    @Test
    fun `with On every phone ticked the tap silences it on every phone`() {
        show(alert(BgAlert.LOW), everywhere = true)

        compose.onNodeWithText(ctx.getString(R.string.alarm_silence_on_every_phone)).performClick()
        compose.onNodeWithText(minutes(15)).performClick()

        verify(alerts).silenceEverywhere(BgAlert.SOURCE, KEY, 15)
        verify(alerts, never()).silence(any(), any(), anyOrNull())
        assertThat(closed).isEqualTo(1)
    }

    /** Where the alarm is this phone's only, or there is no link to the others, there is nothing to tick. */
    @Test
    fun `On every phone is not offered where it cannot be done`() {
        show(alert(BgAlert.LOW), everywhere = false)

        compose.onNodeWithText(ctx.getString(R.string.alarm_silence_on_every_phone)).assertDoesNotExist()
    }

    @Test
    fun `Silence all BG alarms pauses every BG alarm but urgent low for 1 h and closes the screen`() {
        show(alert(BgAlert.LOW))
        val before = System.currentTimeMillis()

        compose.onNodeWithText(silenceAll).performClick()

        val until = argumentCaptor<Long>()
        verify(alerts).pause(eq(BgAlert.PAUSABLE), until.capture())
        assertThat(until.firstValue).isAtLeast(before + 60 * 60_000L)
        assertThat(until.firstValue).isAtMost(System.currentTimeMillis() + 60 * 60_000L)
        assertThat(BgAlert.PAUSABLE).doesNotContain(BgAlert.URGENT_LOW)
        assertThat(closed).isEqualTo(1)
    }

    /** A pump alert that is not critical gets 1 h too, but none of the BG alarm's own buttons. */
    @Test
    fun `a pump alert has the lengths and no BG alarm buttons`() {
        show(alert(CoreAlert.COMMAND_FAILED))

        compose.onNodeWithText(oneHour).assertIsDisplayed()
        compose.onNodeWithText(silenceAll).assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(R.string.alarm_silence_on_every_phone)).assertDoesNotExist()
    }

    /** An alarm that ends when it is answered has no length to promise: Mute 5 min, Silence, Silence and close. */
    @Test
    fun `an alarm that ends when it is answered keeps Mute 5 min and Silence and close`() {
        show(alert(CoreAlert.AUTOMATION_ALARM, lifetime = AlertLifetime.EVENT))

        compose.onNodeWithText(ctx.getString(CoreUiR.string.mute5min)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.alarm_silence_for_label)).assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.alarm_silence_and_close)).performClick()

        verify(alerts).silence(CoreAlert.AUTOMATION_ALARM.source, KEY, null)
        assertThat(closed).isEqualTo(1)
    }

    private companion object {

        const val KEY = "test"
    }
}
