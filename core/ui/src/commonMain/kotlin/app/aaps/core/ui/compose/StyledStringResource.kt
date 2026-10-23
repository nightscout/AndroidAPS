package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef

/** A value for [styledStringResource], and its style. No style keeps the style of the text around it. */
data class StyledArg(val text: String, val style: SpanStyle? = null)

/**
 * Like [stringResource] with format arguments, but each argument can have its own style.
 *
 * Use it when part of a text is shown in another style, for example a bold amount. The text stays one
 * translatable template, so a translator can change the order of the parts and the words between them.
 * Joining the parts in code does not allow this, and it gives the wrong order in right-to-left languages.
 *
 * Every placeholder in the template must be a string (`%1$s`), because the arguments are already
 * formatted.
 */
@Composable
fun styledStringResource(template: TextRef, vararg args: StyledArg): AnnotatedString {
    val markers = Array(args.size) { "$MARKER_START$it$MARKER_END" }
    return styleArguments(stringResource(template, *markers), args.toList())
}

/** The same as [styledStringResource], for code outside a Composable. */
fun TextResolver.styledString(template: TextRef, vararg args: StyledArg): AnnotatedString {
    val markers = Array(args.size) { "$MARKER_START$it$MARKER_END" }
    return styleArguments(gs(template, *markers), args.toList())
}

/**
 * Replaces each marker in [formatted] with its argument, in that argument's style. Kept apart from
 * [styledStringResource] so that it can be tested without Compose.
 */
internal fun styleArguments(formatted: String, args: List<StyledArg>): AnnotatedString = buildAnnotatedString {
    var position = 0
    for (match in MARKER.findAll(formatted)) {
        append(formatted.substring(position, match.range.first))
        val arg = args[match.groupValues[1].toInt()]
        if (arg.style != null) withStyle(arg.style) { append(arg.text) } else append(arg.text)
        position = match.range.last + 1
    }
    append(formatted.substring(position))
}

// Characters from the private use area, which no translation contains.
private const val MARKER_START = ''
private const val MARKER_END = ''
private val MARKER = Regex("$MARKER_START(\\d+)$MARKER_END")
