package app.aaps.core.objects.extensions

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import app.aaps.core.interfaces.resources.ResourceHelper

fun String.formatBold(): String =
    "<b>$this</b>"

fun String.formatColor(rh: ResourceHelper, @ColorRes colorId: Int): String =
    "<font color='" + rh.gc(colorId) + "'>" + this + "</font>"

fun String.formatColor(context: Context?, rh: ResourceHelper, @AttrRes attributeId: Int): String =
    "<font color='" + rh.gac(context, attributeId) + "'>" + this + "</font>"

