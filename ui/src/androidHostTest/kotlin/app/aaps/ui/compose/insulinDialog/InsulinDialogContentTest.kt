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
import androidx.compose.ui.test.performClick
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

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
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

    private fun setInsulinDialog(uiState: InsulinDialogUiState, onNavigateBack: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                InsulinDialogContent(
                    uiState = uiState,
                    bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
                    iob = IobUiState(),
                    cob = CobUiState(),
                    dateString = "2024-01-01",
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
}
