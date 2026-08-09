package app.aaps.spike.cmp

import androidx.compose.runtime.Composable
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef

/** Android resolves through AAPT, exactly as `:core:ui` does today. */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.AndroidRes ->
        if (ref.args.isEmpty()) androidx.compose.ui.res.stringResource(ref.id)
        else androidx.compose.ui.res.stringResource(ref.id, *ref.args.toTypedArray())

    is TextRef.Named      -> {
        val id = KeysStringIds.idOf(ref.name)
        if (id == null) ref.name else androidx.compose.ui.res.stringResource(id)
    }
}
