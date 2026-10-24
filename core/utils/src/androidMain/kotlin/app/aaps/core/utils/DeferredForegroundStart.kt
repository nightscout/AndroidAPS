package app.aaps.core.utils

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Runs the given action on the main thread when the app process is in foreground
 * (lifecycle state at least STARTED). If already foreground, runs immediately.
 * Otherwise defers until the process next enters foreground.
 *
 * Android 12+ forbids startForegroundService from background contexts; this helper
 * lets callers safely schedule such starts without crashing. Cancel via [cancel]
 * when the scheduling object is no longer alive (e.g. plugin onStop).
 */
class DeferredForegroundStart {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var observer: DefaultLifecycleObserver? = null

    fun start(action: () -> Unit) {
        mainHandler.post {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                action()
                return@post
            }
            // Drop any previously pending observer; latest request wins.
            observer?.let { lifecycle.removeObserver(it) }
            val obs = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    lifecycle.removeObserver(this)
                    observer = null
                    action()
                }
            }
            observer = obs
            lifecycle.addObserver(obs)
        }
    }

    fun cancel() {
        mainHandler.post {
            observer?.let {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
                observer = null
            }
        }
    }
}
