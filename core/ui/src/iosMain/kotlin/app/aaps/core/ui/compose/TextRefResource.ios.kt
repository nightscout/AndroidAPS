package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef

/**
 * PLACEHOLDER. iOS has no string table yet, so this returns something readable rather than the real
 * translation.
 *
 * It exists because `expect` needs an `actual` on every target, and without an iOS one the module
 * would not compile for iOS at all - which is the check that keeps commonMain honest. Nothing on
 * iOS runs yet, so nothing shows these strings to anyone.
 *
 * When iOS gains a string table, this is the only place that has to change:
 * - [TextRef.Named] already carries the name from `strings.xml` and the module that owns it, which
 *   is exactly what a `.strings` lookup needs. That is the form to aim for.
 * - [TextRef.AndroidRes] carries a number that means nothing off Android. Files still using it have
 *   to move to [TextRef.Named] first, so it deliberately has no sensible answer here.
 */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.Named      -> ref.name
    is TextRef.AndroidRes -> "?"
}
