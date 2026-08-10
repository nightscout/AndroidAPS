package app.aaps.core.interfaces.resources

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs

interface ResourceHelper {

    fun gs(@StringRes id: Int): String
    fun gs(@StringRes id: Int, vararg args: Any?): String
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String
    fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String

    /**
     * Resolves a [TextRef] outside Compose. Inside Compose use
     * `app.aaps.core.ui.compose.stringResource` instead.
     *
     * Every non-Compose reader of a preference title goes through here, which is the point: a module
     * that stops naming resource ids in its own code changes only this method, not its call sites.
     *
     * [TextRef.Named] still ends up in `Resources` on Android - the name is turned into an id first -
     * so locale matching behaves exactly as it does for [TextRef.AndroidRes].
     */
    fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.AndroidRes ->
            if (ref.args.isEmpty()) gs(ref.id)
            else gs(ref.id, *ref.args.toTypedArray())

        is TextRef.Named      -> {
            val id = keysIdOf(ref)
            when {
                id == null          -> ref.name
                ref.args.isEmpty()  -> gs(id)
                else                -> gs(id, *ref.args.toTypedArray())
            }
        }
    }

    /** Same, with format arguments - mirrors `gs(id, vararg)`. */
    fun gs(ref: TextRef, vararg args: Any): String = gs(ref.withArgs(*args))

    /** Same, but always in English - used to build the search index. */
    fun gsNotLocalised(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.AndroidRes -> gsNotLocalised(ref.id, *ref.args.toTypedArray())
        is TextRef.Named      -> keysIdOf(ref)
            ?.let { gsNotLocalised(it, *ref.args.toTypedArray()) }
            ?: ref.name
    }

    @ColorInt fun gc(@ColorRes id: Int): Int
    fun gd(@DrawableRes id: Int): Drawable?
    fun gb(@BoolRes id: Int): Boolean
    fun gcs(@ColorRes id: Int): String
    fun gsa(@ArrayRes id: Int): Array<String>
    fun openRawResourceFd(@RawRes id: Int): AssetFileDescriptor?

    fun decodeResource(id: Int): Bitmap
    fun getDisplayMetrics(): DisplayMetrics
    fun dpToPx(dp: Int): Int
    fun dpToPx(dp: Float): Int
    fun shortTextMode(): Boolean
}

/**
 * Resolves a [TextRef.Named] that this module can see.
 *
 * `:core:interfaces` depends on `:core:keys` and nothing else that owns strings, so only `keys` is
 * resolvable here. A name owned by another module falls back to showing the raw name - visibly wrong
 * rather than silently blank.
 *
 * That is not a gap in practice today: the `ui`-owned names are used from Composables, and
 * `app.aaps.core.ui.compose.stringResource` sits in `:core:ui`, which can see both maps. If a
 * non-Compose caller ever needs a `ui` name, this is the place that has to learn about it - probably
 * as a registry rather than another branch.
 */
private fun keysIdOf(ref: TextRef.Named): Int? =
    if (ref.owner == "keys") KeysStringIds.idOf(ref.name) else null
