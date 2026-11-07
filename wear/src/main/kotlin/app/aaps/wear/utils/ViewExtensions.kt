package app.aaps.wear.utils

import android.view.View

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE
fun Boolean.toVisibilityKeepSpace() = if (this) View.VISIBLE else View.INVISIBLE
