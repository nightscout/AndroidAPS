package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.UiStringIds

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
        val id = androidIdOf(ref)
        when {
            id == null         -> ref.name
            ref.args.isEmpty() -> stringResource(id)
            else               -> stringResource(id, *ref.args.toTypedArray())
        }
    }
}

/**
 * Which module's generated id map to look in.
 *
 * A name is only unique within one module, so this dispatches on the owner rather than trying the
 * maps in some order - `ns_wifi_ssids` exists in both with different translations, and guessing
 * would silently pick one.
 *
 * `:core:ui` can see both maps because it depends on `:core:keys`. A module that converts later adds
 * its own branch here, or this becomes a registry once there are enough of them to be worth one.
 */
private fun androidIdOf(ref: TextRef.Named): Int? = when (ref.owner) {
    "keys" -> KeysStringIds.idOf(ref.name)
    "ui"   -> UiStringIds.idOf(ref.name)
    else   -> null
}

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
