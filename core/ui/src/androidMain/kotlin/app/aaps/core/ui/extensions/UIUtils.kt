package app.aaps.core.ui.extensions

import android.os.Handler
import android.os.Looper

fun runOnUiThread(runnable: Runnable?) = runnable?.let {
    Handler(Looper.getMainLooper()).post(it)
}
