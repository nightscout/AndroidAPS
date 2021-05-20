package info.nightscout.androidaps.utils.extensions

import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import info.nightscout.androidaps.utils.resources.ResourceHelper

fun String.formatBold(): String =
    "<b>$this</b>"

fun String.formatColorFromAttribute (@ColorInt colorId: Int): String =
    "<font color='" + colorId + "'>" + this + "</font>"