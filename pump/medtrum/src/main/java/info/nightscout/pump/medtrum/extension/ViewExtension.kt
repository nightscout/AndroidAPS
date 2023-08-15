package info.nightscout.pump.medtrum.extension

import android.view.View

fun View?.visible() = this?.run { visibility = View.VISIBLE }

fun View?.visible(vararg views: View?) {
    visible()
    for (view in views)
        view.visible()
}
