package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.ui.UiStringIds
import app.aaps.ui.compose.profileManagement.viewmodels.TimeValue
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [TimeValueList] and [TargetValueList]: the time chip and its hour wheel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TimeValueListTest {

    @get:Rule
    val compose = createComposeRule()

    private val dateUtil: DateUtil = mock()

    /**
     * Three entries: 00:00, 02:00 and 04:00. Close together, so the whole range the wheel offers for
     * the middle one (01:00 to 03:00) fits in its five visible rows; the wheel is lazy and composes
     * no more than that.
     */
    private val entries = listOf(TimeValue(0, 1.0), TimeValue(2 * HOUR, 1.1), TimeValue(4 * HOUR, 1.2))

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        whenever(dateUtil.timeStringFromSeconds(any())).thenAnswer { "%02d:00".format(it.getArgument<Int>(0) / HOUR) }
        whenever(dateUtil.hourStringFromSeconds(any())).thenAnswer { "%02d".format(it.getArgument<Int>(0) / HOUR) }
    }

    /** The first entry is always 00:00. */
    @Test
    fun firstTimeChipCannotBeChanged() {
        setTimeValueList()
        compose.onNodeWithText("00:00").assertIsNotEnabled()
    }

    /** The wheel offers only the hours between the two neighbours, so the entries stay in order. */
    @Test
    fun tappingATimeChipOpensTheWheelWithTheHoursBetweenItsNeighbours() {
        setTimeValueList()
        compose.onNodeWithText("02:00").performClick()
        compose.onNode(inWheel("01:00")).assertExists()
        compose.onNode(inWheel("03:00")).assertExists()
        compose.onNode(inWheel("00:00")).assertDoesNotExist()
        compose.onNode(inWheel("04:00")).assertDoesNotExist()
    }

    @Test
    fun pickingAnHourMovesThatEntryAndClosesTheWheel() {
        val changes = mutableListOf<Pair<Int, TimeValue>>()
        setTimeValueList(onEntryChange = { index, entry -> changes += index to entry })
        compose.onNodeWithText("02:00").performClick()
        compose.onNode(inWheel("03:00")).performClick()
        assertThat(changes.last()).isEqualTo(1 to TimeValue(3 * HOUR, 1.1))
        compose.onNode(isPopup()).assertDoesNotExist()
    }

    /** Low and high share one time, so picking an hour moves both. */
    @Test
    fun pickingAnHourInTheTargetListMovesLowAndHigh() {
        val highEntries = entries.map { it.copy(value = it.value + 2.0) }
        val changes = mutableListOf<Triple<Int, TimeValue, TimeValue>>()
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    TargetValueList(
                        title = "Target",
                        lowEntries = entries,
                        highEntries = highEntries,
                        onEntryChange = { index, low, high -> changes += Triple(index, low, high) },
                        onAddEntry = {},
                        onRemoveEntry = {},
                        lowRange = 0.0..10.0,
                        highRange = 0.0..10.0
                    )
                }
            }
        }
        compose.onNodeWithText("04:00").performClick()
        compose.onNode(inWheel("05:00")).performClick()
        assertThat(changes.last()).isEqualTo(Triple(2, TimeValue(5 * HOUR, 1.2), TimeValue(5 * HOUR, 3.2)))
    }

    /**
     * The basal tab draws its name itself, next to the sum, so the list does not draw it again. The rows
     * must still carry it for a screen reader: with an empty title each one was read only as "at 08:00".
     */
    @Test
    fun aHiddenTitleStillNamesTheRowsForAScreenReader() {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    TimeValueList(
                        title = "Basal",
                        entries = entries,
                        onEntryChange = { _, _ -> },
                        onAddEntry = {},
                        onRemoveEntry = {},
                        valueRange = 0.0..10.0,
                        unitLabel = "U/h",
                        showTitle = false
                    )
                }
            }
        }
        compose.onNodeWithText("Basal [U/h]").assertDoesNotExist()
        assertThat(compose.onAllNodesWithContentDescription("Basal [U/h]", substring = true).fetchSemanticsNodes()).isNotEmpty()
    }

    /**
     * The value must be readable at any size. On a phone with a row about 255 dp wide, the field was left
     * with about 24 dp, less than its own padding, so the user edited values they could not see.
     * Worst case: the longest value of the range and a large font.
     *
     * From 240 dp: Android phones are at least 320 dp wide, and the editor card leaves about 255 dp of that.
     * The window is wider than the default 320 dp, or a wider list would be cut to that.
     */
    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun theValueAndTheButtonsFitAtAnyWidthAndFontSize() {
        var window by mutableStateOf(320 to 1f)
        setSizedList { window }
        val problems = mutableListOf<String>()
        val shapes = mutableSetOf<String>()
        for (fontScale in listOf(1f, 1.3f, 1.5f, 2f)) {
            for (width in 240..600 step 20) {
                window = width to fontScale
                compose.waitForIdle()
                val size = "$width dp, font $fontScale"
                val padding = with(compose.density) { 32.dp.toPx() }
                val chipPadding = with(compose.density) { 32.dp.toPx() }
                val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                if (fields.isEmpty()) problems += "$size: no value field"
                fields.forEach { field ->
                    val layout = mutableListOf<TextLayoutResult>().also { field.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
                    val textWidth = layout.getLineRight(0) - layout.getLineLeft(0)
                    if (field.boundsInRoot.width < textWidth + padding) problems += "$size: field ${field.boundsInRoot.width} for text $textWidth"
                }
                // The time must be readable too: a cut "02" looks like the time "0"
                for (time in listOf("00:00", "02:00", "04:00")) {
                    val chip = compose.onNode(hasContentDescription(time, substring = false), useUnmergedTree = true).fetchSemanticsNode()
                    val layout = chip.textLayout()
                    val textWidth = layout.getLineRight(0) - layout.getLineLeft(0)
                    if (chip.boundsInRoot.width < textWidth + chipPadding) problems += "$size: chip ${chip.boundsInRoot.width} for text $textWidth"
                }
                val right = with(compose.density) { width.dp.toPx() } + 1f
                compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                    if (node.boundsInRoot.right > right) problems += "$size: a control ends at ${node.boundsInRoot.right}, outside $right"
                }
                shapes += when {
                    compose.onAllNodes(hasText("02", substring = false)).fetchSemanticsNodes().isNotEmpty() -> "hour"
                    fields[0].boundsInRoot.top >= compose.onAllNodes(hasText("00:00")).fetchSemanticsNodes().first().boundsInRoot.bottom -> "two lines"
                    else -> "full"
                }
            }
        }
        assertThat(problems).isEmpty()
        // Otherwise the sweep would pass without ever testing one of the three shapes
        assertThat(shapes).containsExactly("full", "hour", "two lines")
    }

    /** Only the chip is short: the screen reader still says the full time. */
    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun aChipWithOnlyTheHourKeepsTheFullTimeForAScreenReader() {
        var window by mutableStateOf(600 to 1f)
        setSizedList { window }
        val width = (180..600).first { candidate ->
            window = candidate to 1f
            compose.waitForIdle()
            compose.onAllNodes(hasText("02", substring = false)).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(compose.onAllNodesWithContentDescription("02:00").fetchSemanticsNodes()).isNotEmpty()
        assertThat(width).isLessThan(600)
    }

    /** The layout of the text in this node or below it. */
    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val action = config.getOrNull(SemanticsActions.GetTextLayoutResult)
        return if (action != null) mutableListOf<TextLayoutResult>().also { action.action?.invoke(it) }.single()
        else children.first { it.config.contains(SemanticsActions.GetTextLayoutResult) || it.children.isNotEmpty() }.textLayout()
    }

    private fun setSizedList(window: () -> Pair<Int, Float>) {
        val values = entries.map { it.copy(value = 100.0) }
        compose.setContent {
            val (width, fontScale) = window()
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDateUtil provides dateUtil,
                LocalDensity provides Density(density.density, fontScale)
            ) {
                MaterialTheme {
                    Box(Modifier.width(width.dp)) {
                        TimeValueList(
                            title = "Basal",
                            entries = values,
                            onEntryChange = { _, _ -> },
                            onAddEntry = {},
                            onRemoveEntry = {},
                            valueRange = 0.01..100.0,
                            step = 0.01,
                            valueFormat = NumberFormat.DECIMAL_2
                        )
                    }
                }
            }
        }
    }

    /** An hour label inside the popup wheel, as opposed to the same text on a chip in the list. */
    private fun inWheel(hourLabel: String) = hasText(hourLabel) and hasAnyAncestor(isPopup())

    private fun setTimeValueList(onEntryChange: (Int, TimeValue) -> Unit = { _, _ -> }) {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    TimeValueList(
                        title = "Basal",
                        entries = entries,
                        onEntryChange = onEntryChange,
                        onAddEntry = {},
                        onRemoveEntry = {},
                        valueRange = 0.0..10.0
                    )
                }
            }
        }
    }

    private companion object {

        const val HOUR = 3600
    }
}
