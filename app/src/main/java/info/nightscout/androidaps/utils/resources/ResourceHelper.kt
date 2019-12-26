package info.nightscout.androidaps.utils.resources

import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

interface ResourceHelper {
    fun gs(@StringRes id: Int): String?
    fun gs(@StringRes id: Int, vararg args: Any?): String?
    fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String?
    fun gc(@ColorRes id: Int): Int?
}