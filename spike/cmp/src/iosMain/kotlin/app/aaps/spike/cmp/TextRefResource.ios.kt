package app.aaps.spike.cmp

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef

/**
 * iOS has no resource system wired up here, and does not need one to answer the spike's question.
 *
 * [TextRef.Named] is the form a real iOS client would resolve, through a generated name-to-text table
 * built from the same `strings.xml` - the client only needs the system language, so that table is a
 * small generated file rather than a resource framework. Until it exists, showing the name is honest:
 * it is visibly wrong on screen rather than silently blank.
 *
 * [TextRef.AndroidRes] cannot be resolved off Android by construction. It is an `Int` from AAPT, so
 * a module that still hands one out has not finished moving - which is the point of the type.
 */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.Named      -> ref.name
    is TextRef.AndroidRes -> "?res:${ref.id}"
}
