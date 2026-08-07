package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.TextRef

/**
 * Resolves a [TextRef] to text inside a Composable.
 *
 * Every preference screen funnels through this one function, which is the point: when a module later
 * moves its strings to `commonMain/composeResources`, only this resolver learns about the new
 * negative-token form of [TextRef.Res]. The ~18 call sites do not change again.
 *
 * On Android today [TextRef.Res.id] is always an ordinary `R.string` id, so this is a direct call
 * through to the platform.
 */
@Composable
fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal -> ref.text
    is TextRef.Res     ->
        if (ref.args.isEmpty()) stringResource(ref.id)
        else stringResource(ref.id, *ref.args.toTypedArray())
}

/** Same, for an optional reference - returns null so callers can keep using `?.let { }`. */
@Composable
fun stringResourceOrNull(ref: TextRef?): String? = ref?.let { stringResource(it) }
