package app.aaps.core.ui.compose

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExpandableRowHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var changeLabel: String
    private lateinit var percentageLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        changeLabel = ctx.getString(CoreUiR.string.change)
        percentageLabel = ctx.getString(CoreUiR.string.label_with_colon, LABEL)
    }

    @Test
    fun showsLabelWithColonValueAndChangeButton() {
        var expandClicks = 0
        compose.setContent {
            MaterialTheme {
                ExpandableRowHeader(label = LABEL, expanded = false, onExpand = { expandClicks++ }) {
                    Text(VALUE)
                }
            }
        }
        compose.onNodeWithText(percentageLabel).assertIsDisplayed()
        compose.onNodeWithText(VALUE).assertIsDisplayed()
        compose.onNodeWithText(changeLabel).performClick()
        assertThat(expandClicks).isEqualTo(1)
    }

    @Test
    fun changeButtonIsHiddenWhileExpanded() {
        compose.setContent {
            MaterialTheme {
                ExpandableRowHeader(label = LABEL, expanded = true, onExpand = {}) {
                    Text(VALUE)
                }
            }
        }
        compose.onNodeWithText(changeLabel).assertDoesNotExist()
    }

    /** In a plain Row the value was squeezed next to the label until it wrapped one letter per line. */
    @Test
    fun withBigFontValueMovesToTheNextLineInOnePiece() {
        compose.setContent {
            MaterialTheme {
                WithFontScale(BIG_FONT_SCALE) {
                    Box(Modifier.width(300.dp)) {
                        ExpandableRowHeader(label = LABEL, expanded = false, onExpand = {}) {
                            Text(VALUE)
                        }
                    }
                }
            }
        }
        val label = compose.onNodeWithText(percentageLabel).getUnclippedBoundsInRoot()
        val value = compose.onNodeWithText(VALUE).getUnclippedBoundsInRoot()
        assertThat(value.top).isAtLeast(label.bottom)
        assertThat(compose.onNodeWithText(VALUE).textLineCount()).isEqualTo(1)
        compose.onNodeWithText(changeLabel).assertIsDisplayed()
    }

    private companion object {

        const val LABEL = "Use percentage"
        const val VALUE = "71%"
    }
}
