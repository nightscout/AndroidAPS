package info.nightscout.androidaps.utils

import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.MainApp
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-23.
 */
class ResourceHelper @Inject constructor() {

    fun gs(@StringRes id: Int): String = MainApp.sResources.getString(id)

    fun gs(@StringRes id: Int, vararg args: Any?) = MainApp.sResources.getString(id, *args)

    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        MainApp.sResources.getQuantityString(id, quantity, *args)

    fun gc(@ColorRes id: Int): Int = MainApp.sResources.getColor(id)
}