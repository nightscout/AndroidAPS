package info.nightscout.androidaps.utils.resources

import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.MainApp
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-23.
 */
class ResourceHelperImplementation @Inject constructor(private val mainApp: MainApp) : ResourceHelper {

    override fun gs(@StringRes id: Int): String? = mainApp.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String? = mainApp.getString(id, *args)

    override fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String? =
        mainApp.resources.getQuantityString(id, quantity, *args)

    override fun gc(@ColorRes id: Int): Int = ContextCompat.getColor(mainApp, id)
}