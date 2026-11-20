package app.aaps.plugins.source.eversense

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A new screen session waits until the previous session has left diagnostic mode. */
internal class PositioningSession(
    private val scope: CoroutineScope,
    private val enter: suspend () -> Unit,
    private val read: suspend () -> Unit,
    private val exit: suspend () -> Unit
) {
    private val mutex = Mutex()
    private var job: Job? = null

    fun resume() {
        pause()
        job = scope.launch {
            mutex.withLock {
                var entering = false
                try {
                    delay(500)
                    ensureActive()
                    entering = true
                    enter()
                    while (isActive) {
                        read()
                        delay(500)
                    }
                } finally {
                    if (entering) withContext(NonCancellable) { exit() }
                }
            }
        }
    }

    fun pause() {
        job?.cancel()
        job = null
    }
}
