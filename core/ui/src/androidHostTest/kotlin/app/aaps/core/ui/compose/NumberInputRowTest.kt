package app.aaps.core.ui.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
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
 * #5666: deleting a number left the digit before the last delete as the value. The field showed
 * nothing, but the bolus wizard calculated with 2 g after "20" was deleted, and +/- counted from it.
 *
 * The row is used here the way a screen uses it: what it publishes comes back as its `value`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NumberInputRowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var plusButton: String

    /** Every value the row published, in order. */
    private val published = mutableListOf<Double>()

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val stepText = ctx.getString(CoreUiR.string.value_with_unit, "1", UNIT)
        plusButton = ctx.getString(CoreUiR.string.a11y_plus_button_description, ctx.getString(CoreUiR.string.time), stepText)
    }

    private fun setRow(initial: Double, valueRange: ClosedFloatingPointRange<Double>) {
        compose.setContent {
            MaterialTheme {
                var value by remember { mutableDoubleStateOf(initial) }
                NumberInputRow(
                    labelRef = TextRef.AndroidRes(CoreUiR.string.time),
                    value = value,
                    onValueChange = {
                        value = it
                        published += it
                    },
                    valueRange = valueRange,
                    step = 1.0,
                    unitLabel = TextRef.Literal(UNIT)
                )
            }
        }
    }

    private fun field(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction())

    private fun fieldText(): String = field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** Types [texts] one after the other, as the field shows them while the user types or deletes. */
    private fun type(vararg texts: String) {
        field().performClick()
        texts.forEach { field().performTextReplacement(it) }
    }

    @Test
    fun anEmptiedFieldIsZeroAtOnce() {
        setRow(initial = 0.0, valueRange = 0.0..100.0)

        // "20", one digit deleted, then the other
        type("20", "2")
        field().performTextClearance()

        // Still in the field: the value must not wait for focus loss
        field().assertIsFocused()
        assertThat(published.last()).isEqualTo(0.0)
        assertThat(fieldText()).isEmpty()
    }

    @Test
    fun plusCountsFromZeroAfterTheFieldWasEmptied() {
        setRow(initial = 0.0, valueRange = 0.0..100.0)
        type("20", "2")
        field().performTextClearance()

        compose.onNodeWithContentDescription(plusButton).performClick()

        assertThat(published.last()).isEqualTo(1.0)
    }

    /**
     * Where 0 is not allowed (the wizard's percentage, 10 - 200 %), an empty field is no value yet.
     * Publishing the lowest value would write it into the field while the user types again.
     */
    @Test
    fun anEmptiedFieldWithoutZeroInTheRangeWaitsAndStaysEmpty() {
        setRow(initial = 100.0, valueRange = 10.0..200.0)

        type("150", "15")
        field().performTextClearance()

        assertThat(published.last()).isEqualTo(15.0)
        assertThat(fieldText()).isEmpty()
    }

    private companion object {

        const val UNIT = "g"
    }
}
