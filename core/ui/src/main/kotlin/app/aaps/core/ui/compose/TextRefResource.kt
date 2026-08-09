package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef

/**
 * Resolves a [TextRef] to text inside a Composable.
 *
 * Every preference screen funnels through this one function, which is the point: a module that
 * changes how it stores its strings changes only this resolver. The ~18 call sites do not change
 * again.
 *
 * Both resource forms end up in the platform `stringResource` on Android. [TextRef.AndroidRes]
 * carries the id already; [TextRef.Named] carries the name from `strings.xml` and is turned into an
 * id first, so Android keeps doing its own locale matching in both cases.
 */
@Composable
fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.AndroidRes ->
        if (ref.args.isEmpty()) stringResource(ref.id)
        else stringResource(ref.id, *ref.args.toTypedArray())

    is TextRef.Named      -> {
        val id = KeysStringIds.idOf(ref.name)
        when {
            id == null         -> ref.name
            ref.args.isEmpty() -> stringResource(id)
            else               -> stringResource(id, *ref.args.toTypedArray())
        }
    }
}

/** Same, for an optional reference - returns null so callers can keep using `?.let { }`. */
@Composable
fun stringResourceOrNull(ref: TextRef?): String? = ref?.let { stringResource(it) }
