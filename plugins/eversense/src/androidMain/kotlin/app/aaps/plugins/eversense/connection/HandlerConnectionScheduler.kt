package app.aaps.plugins.eversense.connection

import android.os.Handler
import android.os.Looper

internal class HandlerConnectionScheduler : EversenseConnectionController.Scheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun after(delayMs: Long, action: () -> Unit): EversenseConnectionController.Task {
        val task = Runnable { action() }
        handler.postDelayed(task, delayMs)
        return EversenseConnectionController.Task { handler.removeCallbacks(task) }
    }
}
