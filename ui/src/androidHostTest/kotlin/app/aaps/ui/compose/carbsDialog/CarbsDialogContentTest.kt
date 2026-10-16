package app.aaps.ui.compose.carbsDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [CarbsDialogContent]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarbsDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var closeLabel: String
    private lateinit var durationExpandButton: String
    private lateinit var durationPlusButton: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
        val durationLabel = ctx.getString(CoreUiR.string.duration_label)
        durationExpandButton = ctx.getString(CoreUiR.string.a11y_expand_button_description, durationLabel)
        durationPlusButton = ctx.getString(
            CoreUiR.string.a11y_plus_button_description,
            durationLabel,
            ctx.getString(CoreUiR.string.value_with_unit, "1", ctx.getString(InterfacesR.string.shorthour))
        )
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        setCarbsDialog(onNavigateBack = { back = true })
        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }

    /** Only extended carbs use the duration, so it is folded and is not a second open field under the carbs. It can still be nudged. */
    @Test
    fun durationIsFoldedAndCanBeNudged() {
        var duration = -1.0
        setCarbsDialog(onDurationChange = { duration = it })
        compose.onNodeWithContentDescription(durationExpandButton).assertExists()
        compose.onNodeWithContentDescription(durationPlusButton).performScrollTo().performClick()
        assertThat(duration).isEqualTo(1.0)
    }

    private fun setCarbsDialog(onNavigateBack: () -> Unit = {}, onDurationChange: (Double) -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                CarbsDialogContent(
                    uiState = CarbsDialogUiState(),
                    bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
                    iob = IobUiState(),
                    cob = CobUiState(),
                    dateString = "2024-01-01",
                    timeString = "12:00",
                    onHypoChange = {},
                    onEatingSoonChange = {},
                    onActivityChange = {},
                    onCarbsChange = {},
                    onAddCarbs = {},
                    onTimeOffsetChange = {},
                    onAlarmChange = {},
                    onDurationChange = onDurationChange,
                    onBolusReminderChange = {},
                    onNotesChange = {},
                    onDateClick = {},
                    onTimeClick = {},
                    onSettingsClick = null,
                    onNavigateBack = onNavigateBack,
                    onConfirmClick = {}
                )
            }
        }
    }
}
