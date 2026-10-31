package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density

/** Font scale used to test layouts with a big system font. 1.8 is what showed the problems on a phone. */
internal const val BIG_FONT_SCALE = 1.8f

/** Shows [content] as if the user had set the system font to [fontScale]. */
@Composable
internal fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
}

/** Number of lines the text of this node is laid out in. A text squeezed too narrow shows more than one. */
internal fun SemanticsNodeInteraction.textLineCount(): Int {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single().lineCount
}
