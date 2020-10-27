package info.nightscout.androidaps.utils.ui

import info.nightscout.androidaps.utils.extensions.runOnUiThread

class UIRunnable (val runnable: Runnable) : Runnable {
    override fun run() {
        runOnUiThread(runnable)
    }
}