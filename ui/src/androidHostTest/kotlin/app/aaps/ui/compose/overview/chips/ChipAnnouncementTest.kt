package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a screen reader says for the overview chips, and in particular that it says the value ONCE.
 *
 * The chips show a bare value - "1.20 U", "45 g" - with no noun, so the name has to come from a
 * contentDescription. The trap is where to put the value: a contentDescription on a node that
 * merges its descendants does NOT replace their text. `SemanticsNode.emitFakeNodes` inserts the
 * description as an extra child instead (its "Fake node for contentDescription clobbering issue"
 * branch), so the child Text is still read. Putting the value in the description as well as the
 * Text has it announced twice - "IOB: 1.20 U, 1.20 U".
 *
 * So the description carries only the noun, and these tests pin that: the description is exactly
 * the label, and the value appears exactly once in the tree.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ChipAnnouncementTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `iob chip is named IOB and says its value once`() {
        compose.setContent {
            MaterialTheme {
                IobChip(state = IobUiState(text = "1.20 U", iobTotal = 1.2), onClick = {})
            }
        }
        // Exactly the noun - if the value leaks in here it is spoken twice.
        compose.onNodeWithContentDescription("IOB").assertContentDescriptionEquals("IOB")
        compose.onAllNodesWithText("1.20 U").assertCountEquals(1)
    }

    @Test
    fun `cob chip is named COB and says its value once`() {
        compose.setContent {
            MaterialTheme {
                CobChip(state = CobUiState(text = "45 g", carbsReq = 0, cobValue = 45.0))
            }
        }
        compose.onNodeWithContentDescription("COB").assertContentDescriptionEquals("COB")
        compose.onAllNodesWithText("45 g").assertCountEquals(1)
    }

    /**
     * The COB chip has no onClick, so unlike its six siblings it does not merge on its own and
     * needs an explicit mergeDescendants. Without it the noun and the value are two separate stops.
     */
    @Test
    fun `cob chip merges into a single node`() {
        compose.setContent {
            MaterialTheme {
                CobChip(state = CobUiState(text = "45 g", carbsReq = 0, cobValue = 45.0))
            }
        }
        val node = compose.onNodeWithContentDescription("COB").fetchSemanticsNode()
        assert(node.children.isEmpty()) { "COB chip should merge its children, found ${node.children.size}" }
    }
}
