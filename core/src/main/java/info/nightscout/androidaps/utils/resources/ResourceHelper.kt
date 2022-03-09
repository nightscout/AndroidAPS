package info.nightscout.androidaps.utils.resources

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.*

interface ResourceHelper {
    fun gs(@StringRes id: Int): String
    fun gs(@StringRes id: Int, vararg args: Any?): String
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String
    fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String
    @ColorInt fun gc(@ColorRes id: Int): Int
    fun gd(@DrawableRes id: Int): Drawable?
    fun gb(@BoolRes id :Int) : Boolean
    fun gcs(@ColorRes id: Int): String
    fun gsa(@ArrayRes id:Int): Array<String>
    fun openRawResourceFd(@RawRes id : Int) : AssetFileDescriptor?

    fun decodeResource(id : Int) : Bitmap
    fun getDisplayMetrics(): DisplayMetrics
    fun dpToPx(dp: Int): Int
    fun dpToPx(dp: Float): Int
    fun shortTextMode(): Boolean

    /**
     * Get Attribute Color based on theme style
     */
    @ColorInt fun gac(@AttrRes attributeId: Int): Int
    /**
     * Get Attribute Color based on theme style for specified context
     */
    @ColorInt fun gac(context: Context, @AttrRes attributeId: Int): Int
}
