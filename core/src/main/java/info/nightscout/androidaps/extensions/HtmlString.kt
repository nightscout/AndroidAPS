package info.nightscout.androidaps.extensions

import androidx.annotation.ColorRes
import info.nightscout.androidaps.utils.resources.ResourceHelper

fun String.formatBold(): String =
    "<b>$this</b>"

fun String.formatColor(rh: ResourceHelper, @ColorRes colorId: Int): String =
    "<font color='" + rh.gc(colorId) + "'>" + this + "</font>"

