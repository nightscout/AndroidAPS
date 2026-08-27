package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.InterfacesStringIds
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.UiStringIds

/**
 * Both resource forms end up in the platform `stringResource`. [TextRef.AndroidRes] carries the id
 * already; [TextRef.Named] carries the name from `strings.xml` and is turned into an id first, so
 * Android keeps doing its own locale matching in both cases.
 */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.AndroidRes ->
        if (ref.args.isEmpty()) stringResource(ref.id)
        else stringResource(ref.id, *ref.args.forFormat())

    is TextRef.Named      -> {
        val id = androidIdOf(ref)
        when {
            id == null         -> ref.name
            ref.args.isEmpty() -> stringResource(id)
            else               -> stringResource(id, *ref.args.forFormat())
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
 * `:core:ui` can see its own three maps directly because it depends on `:core:keys` and
 * `:core:interfaces`. Everything else - the plugin and pump modules, which `:core:ui` sits below -
 * arrives through [TextRefIdRegistry], registered from `:app`. Without that fallback an unknown
 * owner resolves to null and the raw name is drawn, which is how `format_carbs` once appeared on the
 * overview instead of "12 g".
 */
private fun androidIdOf(ref: TextRef.Named): Int? = when (ref.owner) {
    "keys"       -> KeysStringIds.idOf(ref.name)
    "ui"         -> UiStringIds.idOf(ref.name)
    "interfaces" -> InterfacesStringIds.idOf(ref.name)
    else         -> TextRefIdRegistry.idOf(ref)
}

/**
 * Compose's `stringResource(id, vararg Any)` will not take a null, but `TextRef.args` may hold one
 * because the resource id form it replaces - `gs(id, vararg Any?)` - always could.
 *
 * `String.format` renders a null `%s` as the text "null", so substituting it here produces exactly the
 * same output as the non-Compose path. A null against a numeric conversion still fails, as it did
 * before.
 */
private fun List<Any?>.forFormat(): Array<Any> = Array(size) { this[it] ?: "null" }
