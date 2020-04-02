package info.nightscout.androidaps.utils

import android.os.Handler
import android.view.View
import info.nightscout.androidaps.MainApp

/**
 * Created by adrian on 2019-12-20.
 */

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE

fun runOnUiThread(theRunnable: Runnable?) {
    val mainHandler = Handler(MainApp.instance().applicationContext.mainLooper)
    theRunnable?.let { mainHandler.post(it) }
}