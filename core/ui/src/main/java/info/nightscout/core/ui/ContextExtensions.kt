package info.nightscout.core.ui

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.getThemeColor(@AttrRes attribute: Int) =
    TypedValue().let {
        theme.resolveAttribute(attribute, it, true)
        it.data
    }
