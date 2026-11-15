package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ActionButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    private val longLabel = "Vollständige Synchronisierung"

    private fun show(width: Dp, vararg labels: String, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(width)) {
                        ActionButtons(labels.map { ScreenAction(label = it, icon = Icons.Filled.Refresh, onClick = {}) })
                    }
                }
            }
        }
    }

    /** Bounds of the whole button: the button merges its icon and text into one node. */
    private fun bounds(label: String): DpRect = compose.onNodeWithText(label).getBoundsInRoot()

    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    /**
     * The old layout kept "Changer le" on the one allowed line and dropped "patch" without any sign.
     * `hasVisualOverflow` is not used: for centred text the layout this API returns is as wide as the
     * space offered, not as the text, so it reports an overflow for every label that fits.
     */
    private fun assertWholeLabelShown(label: String, lines: Int = 1) {
        val layout = textLayout(label)
        val lastLine = layout.lineCount - 1
        assertThat(layout.lineCount).isEqualTo(lines)
        assertThat(layout.multiParagraph.didExceedMaxLines).isFalse()
        assertThat(layout.isLineEllipsized(lastLine)).isFalse()
        assertThat(layout.getLineEnd(lastLine, visibleEnd = true)).isEqualTo(label.length)
    }

    @Test
    fun shortLabels_shareOneRow() {
        show(300.dp, "OK", "Pair")

        assertThat(bounds("OK").top).isEqualTo(bounds("Pair").top)
        assertThat(bounds("OK").width.value).isLessThan(150f)
        assertThat(bounds("Pair").width.value).isLessThan(150f)
    }

    @Test
    fun labelThatDoesNotFitInHalf_getsFullRow() {
        show(300.dp, "OK", longLabel)

        assertThat(bounds(longLabel).top).isGreaterThan(bounds("OK").top)
        assertThat(bounds(longLabel).width.value).isWithin(1f).of(300f)
        assertWholeLabelShown(longLabel)
    }

    @Test
    fun halfWidthButton_waitsForTheNextOneThatFits() {
        show(300.dp, "Pair", longLabel, "OK")

        // "Pair" and "OK" share the first row; the long label does not leave two half-empty rows
        assertThat(bounds("OK").top).isEqualTo(bounds("Pair").top)
        assertThat(bounds("OK").left).isGreaterThan(bounds("Pair").left)
        assertThat(bounds(longLabel).top).isGreaterThan(bounds("Pair").top)
    }

    @Test
    fun noLabelIsCut_issue5339() {
        // Medtrum in French: "Changer le patch" showed as "Changer le"
        show(280.dp, "Actualiser", "Changer le patch", "Dissocier")

        assertWholeLabelShown("Actualiser")
        assertWholeLabelShown("Changer le patch")
        assertWholeLabelShown("Dissocier")
    }

    @Test
    fun veryLargeFont_wrapsLabelToSecondLineInsteadOfCuttingIt() {
        show(280.dp, longLabel, fontScale = 1.5f)

        assertThat(bounds(longLabel).width.value).isWithin(1f).of(280f)
        assertWholeLabelShown(longLabel, lines = 2)
    }
}
