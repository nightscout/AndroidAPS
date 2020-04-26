package info.nightscout.androidaps.utils.resources

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-23.
 */
class ResourceHelperImplementation @Inject constructor(private val context: Context) : ResourceHelper {

    override fun gs(@StringRes id: Int): String = context.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String = context.getString(id, *args)

    override fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        context.resources.getQuantityString(id, quantity, *args)

    override fun gc(@ColorRes id: Int): Int = ContextCompat.getColor(context, id)

    override fun gb(@BoolRes id: Int): Boolean = context.resources.getBoolean(id)

    @SuppressLint("ResourceType")
    override fun gcs(@ColorRes id: Int): String =
        gs(id).replace("#ff", "#")

    override fun gsa(@ArrayRes id: Int): Array<String> =
        context.resources.getStringArray(id)

    override fun openRawResourceFd(id: Int): AssetFileDescriptor =
        context.resources.openRawResourceFd(id)

    override fun getIcon(): Int =
        when {
            Config.NSCLIENT    -> R.mipmap.ic_yellowowl
            Config.PUMPCONTROL -> R.mipmap.ic_pumpcontrol
            else               -> R.mipmap.ic_launcher
        }

    override fun getNotificationIcon(): Int =
        when {
            Config.NSCLIENT    -> R.drawable.ic_notif_nsclient
            Config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            else               -> R.drawable.ic_notif_aaps
        }

    override fun decodeResource(id: Int): Bitmap =
        BitmapFactory.decodeResource(context.resources, id)

    override fun getDisplayMetrics():DisplayMetrics =
        context.resources.getDisplayMetrics()

    override fun dpToPx(dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun shortTextMode() : Boolean = !gb(R.bool.isTablet)
}