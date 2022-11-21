package info.nightscout.core.ui

import android.os.Handler
import android.os.Looper

class UIRunnable (val runnable: Runnable) : Runnable {
    private fun runOnUiThread(theRunnable: Runnable?) = theRunnable?.let {
        Handler(Looper.getMainLooper()).post(it)
    }

    override fun run() {
        runOnUiThread(runnable)
    }
}