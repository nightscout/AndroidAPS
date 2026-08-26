package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.aaps.pump.carelevo.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI test for [CarelevoAlarmScreen] - the blocking modal alarm surface presented by
 * `CarelevoAlarmHost` while a Carelevo alarm is active.
 *
 * The screen is fully stateless (its only inputs are a nullable [CarelevoAlarmUiModel] and three
 * lambdas), so no ViewModel double is needed: the model is passed directly and the callbacks are
 * plain counters.
 *
 * Wrapped in [MaterialTheme] only - the screen reads `MaterialTheme.colorScheme` / `typography` but
 * no `AapsTheme`-specific values, so it needs neither `LocalPreferences` nor a mocked `Preferences`
 * (this mirrors the screen's own `@Preview`).
 *
 * A phone-sized qualifier is forced so the tall alarm card (icon + title + body + three stacked
 * buttons) fits on screen and `assertIsDisplayed` / `performClick` stay meaningful.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CarelevoAlarmScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var primaryClicks = 0
    private var muteClicks = 0
    private var mute5MinClicks = 0

    private fun alarmModel(
        title: String = TITLE,
        content: String = CONTENT
    ) = CarelevoAlarmUiModel(
        appIcon = R.drawable.ic_carelevo_128,
        title = title,
        content = content,
        primaryButtonText = PRIMARY_BUTTON,
        muteButtonText = MUTE_BUTTON,
        mute5minButtonText = MUTE_5MIN_BUTTON
    )

    /** Hosts the screen alone, with the three callbacks wired to the click counters. */
    private fun setScreen(alarm: CarelevoAlarmUiModel?) {
        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = alarm,
                    onPrimaryClick = { primaryClicks++ },
                    onMuteClick = { muteClicks++ },
                    onMute5MinClick = { mute5MinClicks++ }
                )
            }
        }
    }

    /** Hosts the screen stacked on top of a full-screen clickable underlay, to probe the scrim. */
    private fun setScreenOverUnderlay(alarm: CarelevoAlarmUiModel?, onUnderlayClick: () -> Unit) {
        compose.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(UNDERLAY_TAG)
                            .clickable { onUnderlayClick() }
                    )
                    CarelevoAlarmScreen(
                        alarm = alarm,
                        onPrimaryClick = { primaryClicks++ },
                        onMuteClick = { muteClicks++ },
                        onMute5MinClick = { mute5MinClicks++ }
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Content branch
    // ---------------------------------------------------------------------------------------------

    @Test
    fun alarm_rendersTitleContentAndAllThreeButtons() {
        setScreen(alarmModel())

        compose.onNodeWithText(TITLE).assertIsDisplayed()
        compose.onNodeWithText(CONTENT, substring = true).assertIsDisplayed()
        compose.onNodeWithText(MUTE_5MIN_BUTTON).assertIsDisplayed()
        compose.onNodeWithText(MUTE_BUTTON).assertIsDisplayed()
        compose.onNodeWithText(PRIMARY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun allThreeButtons_areEnabled() {
        setScreen(alarmModel())

        compose.onNodeWithText(MUTE_5MIN_BUTTON).assertIsEnabled()
        compose.onNodeWithText(MUTE_BUTTON).assertIsEnabled()
        compose.onNodeWithText(PRIMARY_BUTTON).assertIsEnabled()
    }

    // ---------------------------------------------------------------------------------------------
    // alarm == null early-return branch
    // ---------------------------------------------------------------------------------------------

    @Test
    fun nullAlarm_rendersNothing() {
        setScreen(null)

        compose.onNodeWithText(TITLE).assertDoesNotExist()
        compose.onNodeWithText(CONTENT, substring = true).assertDoesNotExist()
        compose.onNodeWithText(MUTE_5MIN_BUTTON).assertDoesNotExist()
        compose.onNodeWithText(MUTE_BUTTON).assertDoesNotExist()
        compose.onNodeWithText(PRIMARY_BUTTON).assertDoesNotExist()
    }

    @Test
    fun alarmClearedToNull_removesCard() {
        val alarmState = mutableStateOf<CarelevoAlarmUiModel?>(alarmModel())
        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = alarmState.value,
                    onPrimaryClick = { primaryClicks++ },
                    onMuteClick = { muteClicks++ },
                    onMute5MinClick = { mute5MinClicks++ }
                )
            }
        }
        compose.onNodeWithText(TITLE).assertIsDisplayed()

        compose.runOnUiThread { alarmState.value = null }
        compose.waitForIdle()

        compose.onNodeWithText(TITLE).assertDoesNotExist()
        compose.onNodeWithText(PRIMARY_BUTTON).assertDoesNotExist()
    }

    @Test
    fun alarmReplacedByNextAlarm_rendersNewModel() {
        val alarmState = mutableStateOf<CarelevoAlarmUiModel?>(alarmModel(title = TITLE, content = CONTENT))
        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = alarmState.value,
                    onPrimaryClick = { primaryClicks++ },
                    onMuteClick = { muteClicks++ },
                    onMute5MinClick = { mute5MinClicks++ }
                )
            }
        }
        compose.onNodeWithText(TITLE).assertIsDisplayed()

        compose.runOnUiThread {
            alarmState.value = alarmModel(title = SECOND_TITLE, content = SECOND_CONTENT)
        }
        compose.waitForIdle()

        compose.onNodeWithText(TITLE).assertDoesNotExist()
        compose.onNodeWithText(SECOND_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SECOND_CONTENT, substring = true).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // content.isNotBlank() branch
    // ---------------------------------------------------------------------------------------------

    @Test
    fun emptyContent_hidesBodyText_butKeepsTitleAndButtons() {
        setScreen(alarmModel(content = ""))

        compose.onNodeWithText(CONTENT, substring = true).assertDoesNotExist()
        compose.onNodeWithText(TITLE).assertIsDisplayed()
        compose.onNodeWithText(MUTE_5MIN_BUTTON).assertIsDisplayed()
        compose.onNodeWithText(MUTE_BUTTON).assertIsDisplayed()
        compose.onNodeWithText(PRIMARY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun whitespaceOnlyContent_hidesBodyText() {
        setScreen(alarmModel(content = "   \n  "))

        compose.onNodeWithText(TITLE).assertIsDisplayed()
        compose.onNodeWithText(PRIMARY_BUTTON).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // content HTML / newline handling
    // ---------------------------------------------------------------------------------------------

    @Test
    fun htmlContent_isRenderedAsFormattedText_withTagsStripped() {
        setScreen(alarmModel(content = "<b>Replace</b> the patch <i>now</i>"))

        compose.onNodeWithText("Replace the patch now", substring = true).assertIsDisplayed()
        compose.onNodeWithText("<b>", substring = true).assertDoesNotExist()
        compose.onNodeWithText("<i>", substring = true).assertDoesNotExist()
    }

    @Test
    fun multilineContent_newlinesSurviveAsLineBreaks() {
        setScreen(alarmModel(content = "First line\nSecond line"))

        // "\n" is converted to "<br>" before AnnotatedString.fromHtml, so both halves must land in
        // the same body node.
        compose.onNodeWithText("First line", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Second line", substring = true).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // Button callbacks
    // ---------------------------------------------------------------------------------------------

    @Test
    fun primaryButtonClick_invokesOnlyPrimaryCallback() {
        setScreen(alarmModel())

        compose.onNodeWithText(PRIMARY_BUTTON).performClick()
        compose.waitForIdle()

        assertThat(primaryClicks).isEqualTo(1)
        assertThat(muteClicks).isEqualTo(0)
        assertThat(mute5MinClicks).isEqualTo(0)
    }

    @Test
    fun muteButtonClick_invokesOnlyMuteCallback() {
        setScreen(alarmModel())

        compose.onNodeWithText(MUTE_BUTTON).performClick()
        compose.waitForIdle()

        assertThat(muteClicks).isEqualTo(1)
        assertThat(primaryClicks).isEqualTo(0)
        assertThat(mute5MinClicks).isEqualTo(0)
    }

    @Test
    fun mute5MinButtonClick_invokesOnlyMute5MinCallback() {
        setScreen(alarmModel())

        compose.onNodeWithText(MUTE_5MIN_BUTTON).performClick()
        compose.waitForIdle()

        assertThat(mute5MinClicks).isEqualTo(1)
        assertThat(primaryClicks).isEqualTo(0)
        assertThat(muteClicks).isEqualTo(0)
    }

    @Test
    fun primaryButton_isNotLatched_andFiresOnEveryClick() {
        setScreen(alarmModel())

        compose.onNodeWithText(PRIMARY_BUTTON).performClick()
        compose.onNodeWithText(PRIMARY_BUTTON).performClick()
        compose.waitForIdle()

        assertThat(primaryClicks).isEqualTo(2)
    }

    // ---------------------------------------------------------------------------------------------
    // Blocking modal scrim
    // ---------------------------------------------------------------------------------------------

    @Test
    fun scrim_consumesTaps_soContentUnderneathIsNotReachable() {
        var underlayClicks = 0
        setScreenOverUnderlay(alarmModel()) { underlayClicks++ }

        // Top-left corner: always outside the card (it is inset by AapsSpacing.xxLarge horizontally),
        // so the tap lands on the scrim, which must swallow it.
        compose.onRoot().performTouchInput { click(Offset(2f, 2f)) }
        compose.waitForIdle()

        assertThat(underlayClicks).isEqualTo(0)
        assertThat(primaryClicks).isEqualTo(0)
        assertThat(muteClicks).isEqualTo(0)
        assertThat(mute5MinClicks).isEqualTo(0)
    }

    @Test
    fun withoutAlarm_noScrim_soContentUnderneathStaysClickable() {
        var underlayClicks = 0
        setScreenOverUnderlay(null) { underlayClicks++ }

        // Control for scrim_consumesTaps...: same tap, no alarm - it must reach the underlay.
        compose.onRoot().performTouchInput { click(Offset(2f, 2f)) }
        compose.waitForIdle()

        assertThat(underlayClicks).isEqualTo(1)
    }

    private companion object {

        const val TITLE = "Low insulin warning"
        const val CONTENT = "Insulin remaining is below threshold"
        const val SECOND_TITLE = "Patch expired"
        const val SECOND_CONTENT = "Replace the patch immediately"
        const val PRIMARY_BUTTON = "Confirm"
        const val MUTE_BUTTON = "Mute"
        const val MUTE_5MIN_BUTTON = "Mute 5 min"
        const val UNDERLAY_TAG = "underlay"
    }
}
