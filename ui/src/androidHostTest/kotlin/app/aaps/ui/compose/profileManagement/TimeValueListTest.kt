package app.aaps.ui.compose.profileManagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
