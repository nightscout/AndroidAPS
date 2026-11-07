package app.aaps.core.ui.compose

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * The row that is nudged while closed and typed into once opened.
 *
 * The two states are told apart by the chevron, not by the +/-: those are in both, because the
 * point of the row is that it can be changed without opening it. A test that used a stepper as the
 * sign of "open" would pass whatever the row did.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExpandableNumberInputRowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var expandButton: String
    private lateinit var collapseButton: String
    private lateinit var plusButton: String
    private lateinit var minusButton: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val label = ctx.getString(CoreUiR.string.time)
        val stepText = ctx.getString(CoreUiR.string.value_with_unit, "5", UNIT)
        expandButton = ctx.getString(CoreUiR.string.a11y_expand_button_description, label)
        collapseButton = ctx.getString(CoreUiR.string.a11y_collapse_button_description, label)
        plusButton = ctx.getString(CoreUiR.string.a11y_plus_button_description, label, stepText)
        minusButton = ctx.getString(CoreUiR.string.a11y_min_button_description, label, stepText)
    }

    private fun setRow(
        value: Double = 10.0,
        valueRange: ClosedFloatingPointRange<Double> = 0.0..100.0,
        onValueChange: (Double) -> Unit = {},
        expandedContent: (@Composable ColumnScope.() -> Unit)? = null
    ) {
        compose.setContent {
            MaterialTheme {
                ExpandableNumberInputRow(
                    labelRef = TextRef.AndroidRes(CoreUiR.string.time),
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    step = 5.0,
                    unitLabel = TextRef.Literal(UNIT),
                    expandedContent = expandedContent
                )
            }
        }
    }

    @Test
    fun startsClosedAndOffersTheChevronToOpenIt() {
        setRow()

        compose.onNodeWithContentDescription(expandButton).assertIsDisplayed()
        compose.onNodeWithContentDescription(collapseButton).assertDoesNotExist()
    }

    @Test
    fun theChevronOpensAndClosesTheRowAgain() {
        setRow()

        compose.onNodeWithContentDescription(expandButton).performClick()
        compose.onNodeWithContentDescription(collapseButton).assertIsDisplayed()

        // The way back is the half the old "Change" rows never had.
        compose.onNodeWithContentDescription(collapseButton).performClick()
        compose.onNodeWithContentDescription(expandButton).assertIsDisplayed()
    }

    @Test
    fun theValueCanBeSteppedWhileTheRowIsClosed() {
        var changed: Double? = null
        setRow(value = 10.0, onValueChange = { changed = it })

        compose.onNodeWithContentDescription(plusButton).performClick()

        assertThat(changed).isEqualTo(15.0)
    }

    @Test
    fun steppingStopsAtTheEndsOfTheRange() {
        var changed: Double? = null
        setRow(value = 100.0, onValueChange = { changed = it })

        compose.onNodeWithContentDescription(plusButton).assertIsNotEnabled()
        compose.onNodeWithContentDescription(minusButton).performClick()

        assertThat(changed).isEqualTo(95.0)
    }

    /** Only one pair of +/- at a time: the open field brings its own. */
    @Test
    fun theClosedRowGivesUpItsStepperWhenItOpens() {
        setRow()
        compose.onNodeWithContentDescription(expandButton).performClick()

        // One node, not two - the field's pair, with the header's pair gone.
        compose.onAllNodesWithContentDescription(plusButton).assertCountEquals(1)
    }

    @Test
    fun extraContentIsShownOnlyWhileTheRowIsOpen() {
        setRow(expandedContent = { Text(EXTRA) })

        compose.onNodeWithText(EXTRA).assertDoesNotExist()
        compose.onNodeWithContentDescription(expandButton).performClick()
        compose.onNodeWithText(EXTRA).assertIsDisplayed()
    }

    private companion object {

        const val UNIT = "g"
        const val EXTRA = "Set an alarm"
    }
}
