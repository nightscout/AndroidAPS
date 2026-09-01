package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef

/**
 * PLACEHOLDER. Desktop has no string table yet, so this returns something readable rather than the
 * real translation - exactly as the Apple actual does, and for the same reason.
 *
 * It exists because `expect` needs an `actual` on every target, and without one the module would not
 * compile for desktop at all - which is the check that keeps commonMain honest. Nothing on desktop
 * runs yet, so nothing shows these strings to anyone.
 *
 * When desktop gains a string table, this is the only place that has to change. [TextRef.Named]
 * already carries the name from `strings.xml` and the module that owns it, which is what a resource
 * bundle lookup needs; [TextRef.AndroidRes] carries a number that means nothing here, so files still
 * using it have to move to [TextRef.Named] first.
 */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.Named      -> ref.name
    is TextRef.AndroidRes -> "?"
}
