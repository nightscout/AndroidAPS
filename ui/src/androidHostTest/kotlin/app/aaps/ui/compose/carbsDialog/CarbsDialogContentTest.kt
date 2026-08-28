package app.aaps.ui.compose.carbsDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aaps.core.ui.R as CoreUiR
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

/** Robolectric composable test for [CarbsDialogContent]: renders + fires navigate-back (Close). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarbsDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var closeLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
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
                    onDurationChange = {},
                    onBolusReminderChange = {},
                    onNotesChange = {},
                    onDateClick = {},
                    onTimeClick = {},
                    onSettingsClick = null,
                    onNavigateBack = { back = true },
                    onConfirmClick = {}
                )
            }
        }

        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }
}
