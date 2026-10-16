package app.aaps.ui.compose.insulinDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.data.format.NumberFormat
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

/** Robolectric composable test for [InsulinDialogContent]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class InsulinDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var closeLabel: String
    private lateinit var recordOnlyBanner: SemanticsMatcher
    private lateinit var nowLabel: String
    private lateinit var setAlarmLabel: String
    private lateinit var timeExpandButton: String

    /** The date picker field. Its text is hidden from semantics, the field is found by its description. */
    private lateinit var dateField: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
        nowLabel = ctx.getString(CoreUiR.string.carb_time_now)
        setAlarmLabel = ctx.getString(CoreUiR.string.wizard_set_alarm)
        timeExpandButton = ctx.getString(CoreUiR.string.a11y_expand_button_description, ctx.getString(CoreUiR.string.time))
        dateField = ctx.getString(InterfacesR.string.confirmation_line, ctx.getString(CoreUiR.string.date), DATE)
        // Matched by pane title, because the record-only switch has the same text as the banner.
        recordOnlyBanner = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, ctx.getString(InterfacesR.string.bolus_recorded_only))
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        setInsulinDialog(uiState = InsulinDialogUiState(), onNavigateBack = { back = true })
        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }

    /**
     * The banner must not scroll away. The content fits on the test screen, so a swipe would not
     * show it here: check that the banner is outside the scrolling container instead.
     */
    @Test
    fun recordOnlyBannerIsNotInTheScrollingContent() {
        setInsulinDialog(uiState = InsulinDialogUiState(forcedRecordOnly = true))
        compose.onNode(recordOnlyBanner)
            .assertIsDisplayed()
            .assert(!hasAnyAncestor(hasScrollAction()))
    }

    /** A recorded dose is mostly given just now, so the time is folded at "Now". Unlike carb time it has no alarm. */
    @Test
    fun recordOnlyTimeIsFoldedAndHasNoAlarm() {
        setInsulinDialog(uiState = InsulinDialogUiState(recordOnlyChecked = true))
        compose.onNodeWithText(nowLabel).assertExists()
        compose.onNodeWithContentDescription(dateField).assertDoesNotExist()

        compose.onNodeWithContentDescription(timeExpandButton).performScrollTo().performClick()
        compose.onNodeWithContentDescription(dateField).assertExists()
        compose.onNodeWithText(setAlarmLabel).assertDoesNotExist()
    }

    private fun setInsulinDialog(uiState: InsulinDialogUiState, onNavigateBack: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                InsulinDialogContent(
                    uiState = uiState,
                    bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
                    iob = IobUiState(),
                    cob = CobUiState(),
                    dateString = DATE,
                    timeString = "12:00",
                    bolusFormat = NumberFormat.DECIMAL_1,
                    formatAmount = { it.toString() },
                    onEatingSoonChange = {},
                    onRecordOnlyChange = {},
                    onInsulinChange = {},
                    onAddInsulin = {},
                    onTimeOffsetChange = {},
                    onNotesChange = {},
                    onDateClick = {},
                    onTimeClick = {},
                    onInsulinTypeSelect = {},
                    onSettingsClick = null,
                    onNavigateBack = onNavigateBack,
                    onConfirmClick = {}
                )
            }
        }
    }

    private companion object {

        const val DATE = "2024-01-01"
    }
}
