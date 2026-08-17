package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.InterfacesStringIds
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
 * `:core:ui` can see all three maps because it depends on `:core:keys` and `:core:interfaces`. A
 * module that converts later adds its own branch here, or this becomes a registry once there are
 * enough of them to be worth one.
 */
private fun androidIdOf(ref: TextRef.Named): Int? = when (ref.owner) {
    "keys"       -> KeysStringIds.idOf(ref.name)
    "ui"         -> UiStringIds.idOf(ref.name)
    "interfaces" -> InterfacesStringIds.idOf(ref.name)
    else         -> null
}
