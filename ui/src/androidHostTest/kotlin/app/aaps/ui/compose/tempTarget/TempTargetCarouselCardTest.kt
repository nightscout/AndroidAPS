package app.aaps.ui.compose.tempTarget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric composable test for [TempTargetCarouselCard].
 *
 * Pins the four things the card tells the user: which name it shows (preset name, or the temp
 * target reason for the standalone active card), the target value, the duration text, and the
 * reason badge. Also pins that only the active card carries the ACTIVE label, and that an already
 * expired active target reports back through `onExpired`.
 *
 * Reason labels and duration formats are read from the real `:core:ui` resources rather than
 * hard coded, so a translation change cannot make this test lie.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TempTargetCarouselCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()

    private lateinit var ctx: Context
    private lateinit var eatingSoonLabel: String
    private lateinit var hypoLabel: String
    private lateinit var customLabel: String
    private lateinit var activeLabel: String

    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        eatingSoonLabel = ctx.getString(R.string.eatingsoon)
        hypoLabel = ctx.getString(R.string.hypo)
        customLabel = ctx.getString(R.string.custom)
        activeLabel = ctx.getString(R.string.active)
        whenever(dateUtil.now()).thenReturn(now)
        // Two matchers: the second parameter has a default value, so the call the screen makes is
        // still the two argument form and mixing a matcher with a raw value is not allowed.
        whenever(profileUtil.fromMgdlToStringInUnits(anyOrNull(), anyOrNull())).thenReturn("6.7 mmol/L")
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MMOL)
    }

    private fun preset(
        name: String? = null,
        displayName: String? = null,
        reason: TT.Reason = TT.Reason.EATING_SOON,
        targetValue: Double = 120.0,
        durationMs: Long = 45 * 60_000L
    ) = TTPreset(
        id = "p1",
        name = name,
        displayName = displayName,
        reason = reason,
        targetValue = targetValue,
        duration = durationMs
    )

    private fun activeTt(
        reason: TT.Reason = TT.Reason.HYPOGLYCEMIA,
        timestamp: Long = now,
        durationMs: Long = 30 * 60_000L
    ) = TT(
        timestamp = timestamp,
        reason = reason,
        highTarget = 140.0,
        lowTarget = 140.0,
        duration = durationMs
    )

    private fun setCard(
        preset: TTPreset?,
        activeTT: TT?,
        isSelected: Boolean = false,
        onExpired: () -> Unit = {}
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDateUtil provides dateUtil,
                LocalProfileUtil provides profileUtil
            ) {
                MaterialTheme {
                    TempTargetCarouselCard(
                        preset = preset,
                        activeTT = activeTT,
                        remainingTimeMs = null,
                        isSelected = isSelected,
                        units = GlucoseUnit.MMOL,
                        onExpired = onExpired
                    )
                }
            }
        }
    }

    @Test
    fun presetCardShowsDisplayNameTargetAndDuration() {
        setCard(preset = preset(displayName = "Eating soon preset"), activeTT = null)

        compose.onNodeWithText("Eating soon preset").assertIsDisplayed()
        compose.onNodeWithText("6.7 mmol/L").assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.format_mins, 45)).assertIsDisplayed()
    }

    @Test
    fun presetCardFallsBackToNameWhenNoDisplayName() {
        setCard(preset = preset(name = "My custom TT", displayName = null), activeTT = null)

        compose.onNodeWithText("My custom TT").assertIsDisplayed()
    }

    @Test
    fun presetCardShowsReasonBadge() {
        setCard(preset = preset(displayName = "Eating soon preset"), activeTT = null)

        compose.onNodeWithText(eatingSoonLabel).assertIsDisplayed()
    }

    @Test
    fun durationOverAnHourIsShownAsHoursAndMinutes() {
        setCard(preset = preset(displayName = "Long", durationMs = 90 * 60_000L), activeTT = null)

        compose.onNodeWithText(ctx.getString(R.string.format_hour_minute, 1, 30)).assertIsDisplayed()
    }

    @Test
    fun wholeHourDurationDropsTheMinutes() {
        setCard(preset = preset(displayName = "Long", durationMs = 120 * 60_000L), activeTT = null)

        compose.onNodeWithText(ctx.getString(R.string.format_hours_only, 2)).assertIsDisplayed()
    }

    @Test
    fun presetCardDoesNotShowTheActiveLabel() {
        setCard(preset = preset(displayName = "Eating soon preset"), activeTT = null)

        compose.onNodeWithText(activeLabel.uppercase()).assertDoesNotExist()
    }

    @Test
    fun standaloneActiveCardUsesTheReasonAsItsName() {
        // No preset: the name line falls back to the temp target reason, so "Hypo" appears twice —
        // once as the title and once in the badge.
        setCard(preset = null, activeTT = activeTt(reason = TT.Reason.HYPOGLYCEMIA))

        compose.onAllNodesWithText(hypoLabel).assertCountEquals(2)
    }

    @Test
    fun activeCardShowsTheActiveLabelInUpperCase() {
        setCard(preset = null, activeTT = activeTt())

        compose.onNodeWithText(activeLabel.uppercase()).assertIsDisplayed()
    }

    @Test
    fun activeCardWithoutPresetUsesTheTargetsLowValue() {
        setCard(preset = null, activeTT = activeTt())

        // lowTarget 140.0 mg/dL is what gets handed to ProfileUtil for formatting.
        compose.onNodeWithText("6.7 mmol/L").assertIsDisplayed()
    }

    @Test
    fun cardWithNoPresetAndNoActiveTargetFallsBackToCustom() {
        setCard(preset = null, activeTT = null)

        compose.onNodeWithText(customLabel).assertIsDisplayed()
    }

    @Test
    fun expiredActiveTargetReportsBack() {
        var expired = false
        // Started two hours ago with a 30 minute duration: already over.
        whenever(dateUtil.now()).thenReturn(now + 2 * 60 * 60_000L)
        setCard(
            preset = null,
            activeTT = activeTt(timestamp = now, durationMs = 30 * 60_000L),
            onExpired = { expired = true }
        )
        compose.waitForIdle()

        assertThat(expired).isTrue()
    }

    @Test
    fun runningActiveTargetDoesNotReportExpiry() {
        var expired = false
        // Started ten minutes ago with a 30 minute duration: still running.
        whenever(dateUtil.now()).thenReturn(now + 10 * 60_000L)
        setCard(
            preset = null,
            activeTT = activeTt(timestamp = now, durationMs = 30 * 60_000L),
            onExpired = { expired = true }
        )
        compose.waitForIdle()

        assertThat(expired).isFalse()
    }
}
