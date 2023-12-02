package app.aaps.core.interfaces.extensions

import android.os.Handler
import android.os.Looper
import android.view.View

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE
fun Boolean.toVisibilityKeepSpace() = if (this) View.VISIBLE else View.INVISIBLE

fun runOnUiThread(theRunnable: Runnable?) = theRunnable?.let {
    Handler(Looper.getMainLooper()).post(it)
}