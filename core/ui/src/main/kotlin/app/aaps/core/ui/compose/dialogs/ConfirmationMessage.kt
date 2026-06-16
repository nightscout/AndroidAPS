package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.ui.compose.AapsTheme

/**
 * Render structured [ConfirmationLine]s into a themed [AnnotatedString]: each line's [ConfirmationRole] is
 * mapped to a theme color **here, at render time**, and the whole (already-localized) [ConfirmationLine.text]
 * is tinted with it — the renderer never assembles label/value/unit, so translation stays intact.
 * [primaryColor] colors [ConfirmationRole.PRIMARY] lines (pass the host dialog's element color). Lines are
 * newline-separated.
 */
@Composable
fun List<ConfirmationLine>.toAnnotatedString(primaryColor: Color): AnnotatedString {
    // Resolve theme colors once (each is a @Composable read; the build lambda below is not Composable).
    val insulin = AapsTheme.elementColors.insulin
    val carbs = AapsTheme.elementColors.carbs
    val cob = AapsTheme.elementColors.cob
    val warning = MaterialTheme.colorScheme.error
    val info = AapsTheme.elementColors.tempTarget
    fun color(role: ConfirmationRole): Color? = when (role) {
        ConfirmationRole.NORMAL  -> null
        ConfirmationRole.PRIMARY -> primaryColor
        ConfirmationRole.BOLUS   -> insulin
        ConfirmationRole.CARBS   -> carbs
        ConfirmationRole.COB     -> cob
        ConfirmationRole.WARNING -> warning
        ConfirmationRole.INFO    -> info
    }
    return buildAnnotatedString {
        this@toAnnotatedString.forEachIndexed { index, confirmationLine ->
            if (index > 0) append("\n")
            val tint = color(confirmationLine.role)
            if (tint == null) append(confirmationLine.text)
            else withStyle(SpanStyle(color = tint)) { append(confirmationLine.text) }
        }
    }
}
