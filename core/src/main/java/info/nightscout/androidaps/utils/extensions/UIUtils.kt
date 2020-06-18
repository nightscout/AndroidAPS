package info.nightscout.androidaps.utils.extensions

import android.os.Handler
import android.os.Looper
import android.view.View

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE

fun runOnUiThread(theRunnable: Runnable?) = theRunnable?.let {
    Handler(Looper.getMainLooper()).post(it)
}