package app.aaps.ui.compose.overview

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.LocalDateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [EatReminderBannerContent]: the countdown, Stop and +5 min, and Dismiss once it rang. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EatReminderBannerContentTest {

    private val now = 1_000_000L
    private val dateUtil: DateUtil = mock<DateUtil>().also { whenever(it.now()).thenReturn(now) }

    @get:Rule
    val compose = createComposeRule()

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
    }

    /** Set for 20 minutes, 12 min 5 s left. */
    private val running = EatReminderState(startedAt = now - 475_000L, endsAt = now + 725_000L, durationMs = 1_200_000L)
    private val rung = EatReminderState(startedAt = now - 1_200_000L, endsAt = now - 1_000L, durationMs = 1_200_000L)

    private fun show(state: EatReminderState, role: ClientRole = ClientRole.Admin, onStop: () -> Unit = {}, onExtend: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil, LocalClientRole provides role) {
                MaterialTheme {
                    EatReminderBannerContent(state = state, onStop = onStop, onExtend = onExtend)
                }
            }
        }
    }

    @Test
    fun runningTimer_showsTimeLeftAndTheWholeWait() {
        show(running)

        compose.onNodeWithText(ctx.getString(CoreUiR.string.eat_reminder_title)).assertIsDisplayed()
        compose.onNodeWithText("12:05").assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.eat_reminder_total_wait, ctx.getString(CoreUiR.string.format_mins, 20))).assertIsDisplayed()
    }

    @Test
    fun runningTimer_stopAndExtendFire() {
        var stopped = false
        var extended = false
        show(running, onStop = { stopped = true }, onExtend = { extended = true })

        compose.onNodeWithText(ctx.getString(CoreUiR.string.eat_reminder_extend, EatReminder.EXTEND_MINUTES)).performClick()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.stop)).performClick()

        assertThat(extended).isTrue()
        assertThat(stopped).isTrue()
    }

    @Test
    fun rungTimer_offersDismissInsteadOfStop() {
        var stopped = false
        show(rung, onStop = { stopped = true })

        compose.onNodeWithText(ctx.getString(CoreUiR.string.eat_reminder_rung)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.dismiss)).performClick()

        assertThat(stopped).isTrue()
        assertThat(nodesWithText(ctx.getString(CoreUiR.string.stop))).isEqualTo(0)
    }

    /** The master refuses both for a read-only client, so it sees the timer but no buttons. */
    @Test
    fun readOnlyClient_seesTheTimerWithoutButtons() {
        show(running, role = ClientRole.ReadOnly)

        compose.onNodeWithText("12:05").assertIsDisplayed()
        assertThat(nodesWithText(ctx.getString(CoreUiR.string.stop))).isEqualTo(0)
    }

    private fun nodesWithText(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
}
