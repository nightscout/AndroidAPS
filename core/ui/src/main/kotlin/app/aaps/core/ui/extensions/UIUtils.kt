package app.aaps.core.ui.extensions

import android.os.Handler
import android.os.Looper
import android.view.View

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE
fun Boolean.toVisibilityKeepSpace() = if (this) View.VISIBLE else View.INVISIBLE

fun runOnUiThread(runnable: Runnable?) = runnable?.let {
    Handler(Looper.getMainLooper()).post(it)
}

fun runOnUiThreadDelayed(dellayMillis: Long, runnable: Runnable?) = runnable?.let {
    Handler(Looper.getMainLooper()).postDelayed(it, dellayMillis)
}