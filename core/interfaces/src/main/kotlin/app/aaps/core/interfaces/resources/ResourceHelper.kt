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
import app.aaps.core.keys.interfaces.TextRef

interface ResourceHelper {

    fun gs(@StringRes id: Int): String
    fun gs(@StringRes id: Int, vararg args: Any?): String
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String
    fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String

    /**
     * Resolves a [TextRef] outside Compose. Inside Compose use
     * `app.aaps.core.ui.compose.stringResource` instead.
     *
     * Every non-Compose reader of a preference title goes through here, which is the point: when a
     * module later moves its strings out of `res/values`, only this one method has to learn about
     * the new form of [TextRef.AndroidRes].
     */
    fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal -> ref.text
        is TextRef.AndroidRes     ->
            if (ref.args.isEmpty()) gs(ref.id)
            else gs(ref.id, *ref.args.toTypedArray())
    }

    /** Same, but always in English - used to build the search index. */
    fun gsNotLocalised(ref: TextRef): String = when (ref) {
        is TextRef.Literal -> ref.text
        is TextRef.AndroidRes     -> gsNotLocalised(ref.id, *ref.args.toTypedArray())
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
