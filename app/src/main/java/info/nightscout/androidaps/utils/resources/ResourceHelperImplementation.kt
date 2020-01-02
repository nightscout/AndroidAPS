package info.nightscout.androidaps.utils.resources

import android.annotation.SuppressLint
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.BoolRes
import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-23.
 */
class ResourceHelperImplementation @Inject constructor(private val mainApp: MainApp) : ResourceHelper {

    override fun gs(@StringRes id: Int): String = mainApp.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String = mainApp.getString(id, *args)

    override fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        mainApp.resources.getQuantityString(id, quantity, *args)

    override fun gc(@ColorRes id: Int): Int = ContextCompat.getColor(mainApp, id)

    override fun gb(@BoolRes id :Int) : Boolean = mainApp.resources.getBoolean(id)

    @SuppressLint("ResourceType")
    override fun gcs(@ColorRes id: Int): String =
        gs(id).replace("#ff", "#")

    override fun openRawResourceFd(id: Int): AssetFileDescriptor =
        mainApp.resources.openRawResourceFd(id)

    override fun getIcon(): Int {
        return when {
            Config.NSCLIENT    -> R.mipmap.ic_yellowowl
            Config.PUMPCONTROL -> R.mipmap.ic_pumpcontrol
            else               -> R.mipmap.ic_launcher
        }
    }

    override fun getNotificationIcon(): Int {
        return when {
            Config.NSCLIENT    -> R.drawable.ic_notif_nsclient
            Config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            else               -> R.drawable.ic_notif_aaps
        }
    }

    override fun decodeResource(id: Int): Bitmap =
        BitmapFactory.decodeResource(mainApp.resources, id)

    override fun dpToPx(dp: Int): Int {
        val scale = mainApp.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}