package info.nightscout.androidaps.utils

import android.view.View

/**
 * Created by adrian on 2019-12-20.
 */

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE

