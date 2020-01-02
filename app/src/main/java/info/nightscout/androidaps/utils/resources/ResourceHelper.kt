package info.nightscout.androidaps.utils.resources

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import androidx.annotation.BoolRes
import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes

interface ResourceHelper {
    fun gs(@StringRes id: Int): String
    fun gs(@StringRes id: Int, vararg args: Any?): String
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String
    fun gc(@ColorRes id: Int): Int
    fun gb(@BoolRes id :Int) : Boolean
    fun gcs(@ColorRes id: Int): String
    fun openRawResourceFd(@RawRes id : Int) : AssetFileDescriptor?

    fun getIcon() : Int
    fun getNotificationIcon() : Int
    fun decodeResource(id : Int) : Bitmap
    fun dpToPx(dp: Int): Int
}