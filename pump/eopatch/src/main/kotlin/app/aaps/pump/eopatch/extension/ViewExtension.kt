package app.aaps.pump.eopatch.extension

import android.view.View

fun View?.visible() = this?.run { visibility = View.VISIBLE }

fun View?.visible(vararg views: View?) {
    visible()
    for (view in views)
        view.visible()
}

fun View?.invisible() = this?.run { visibility = View.INVISIBLE }

fun View?.invisible(vararg views: View?) {
    invisible()
    for (view in views)
        view.invisible()
}

fun View?.gone() = this?.run { visibility = View.GONE }

fun View?.gone(vararg views: View?) {
    gone()
    for (view in views)
        view.gone()
}

fun View?.setVisibleOrGone(visibleOrGone: Boolean, vararg views: View?) {
    for (view in views)
        if (visibleOrGone) view.visible() else view.gone()
}

fun View?.setVisibleOrGone(visibleOrGone: Boolean) = setVisibleOrGone(visibleOrGone, this)
