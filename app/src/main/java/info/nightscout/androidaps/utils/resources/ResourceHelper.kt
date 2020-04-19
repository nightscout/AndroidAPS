package info.nightscout.androidaps.utils.resources

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.util.DisplayMetrics
import androidx.annotation.*

interface ResourceHelper {
    fun gs(@StringRes id: Int): String
    fun gs(@StringRes id: Int, vararg args: Any?): String
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String
    fun gc(@ColorRes id: Int): Int
    fun gb(@BoolRes id :Int) : Boolean
    fun gcs(@ColorRes id: Int): String
    fun gsa(@ArrayRes id:Int): Array<String>
    fun openRawResourceFd(@RawRes id : Int) : AssetFileDescriptor?

    fun getIcon() : Int
    fun getNotificationIcon() : Int
    fun decodeResource(id : Int) : Bitmap
    fun getDisplayMetrics(): DisplayMetrics
    fun dpToPx(dp: Int): Int
    fun shortTextMode(): Boolean
}