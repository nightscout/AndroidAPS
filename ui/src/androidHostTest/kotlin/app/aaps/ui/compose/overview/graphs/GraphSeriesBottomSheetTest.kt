package app.aaps.ui.compose.overview.graphs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * The series sheet in a window shorter than its content: a large font, a small screen, landscape, or the
 * keyboard open for the height field. Without a scroll the last row of chips was left out and could not be
 * picked (#5478).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h300dp")
class GraphSeriesBottomSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theLastChipCanBeReachedAndSelectedInAShortWindow() {
        // The last chip of the real list, so the test follows the list when a series is added
        assertThat(CONFIGURABLE_SERIES.last()).isEqualTo(SeriesType.ACTIVITY)
        val ctx: Context = RuntimeEnvironment.getApplication()
        val lastChip = ctx.getString(CoreUiR.string.graph_series_activity)
        var selected by mutableStateOf(emptyList<SeriesType>())
        compose.setContent {
            MaterialTheme {
                GraphSeriesBottomSheet(
                    title = "Add graph",
                    selectedSeries = selected,
                    availableSeries = CONFIGURABLE_SERIES,
                    height = 100,
                    onHeightChange = {},
                    onToggle = { selected = selected + it },
                    onDismiss = {}
                )
            }
        }

        // Without the scroll, FlowRow left the last row out (zero size) and a tap on it did nothing
        compose.onNodeWithText(lastChip).performScrollTo().performClick()

        compose.onNodeWithText(lastChip).assertIsSelected()
    }
}
