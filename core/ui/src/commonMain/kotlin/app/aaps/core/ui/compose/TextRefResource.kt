package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs

/**
 * Resolves a [TextRef] to text inside a Composable.
 *
 * Every screen funnels through this one function, which is the point: a module that changes how it
 * stores its strings changes only this resolver, and the call sites do not change again.
 *
 * It is `expect` because finding the text is the one part that cannot be shared. Android looks the
 * reference up through `Resources`, so it keeps doing its own locale matching exactly as before.
 * Every other target needs its own answer.
 */
@Composable
expect fun stringResource(ref: TextRef): String

/**
 * Same, with format arguments - mirrors `androidx.compose.ui.res.stringResource(id, vararg)`, which
 * is what the call sites used before they stopped naming resource ids.
 */
@Composable
fun stringResource(ref: TextRef, vararg formatArgs: Any): String =
    stringResource(ref.withArgs(*formatArgs))

/** Same, for an optional reference - returns null so callers can keep using `?.let { }`. */
@Composable
fun stringResourceOrNull(ref: TextRef?): String? = ref?.let { stringResource(it) }
