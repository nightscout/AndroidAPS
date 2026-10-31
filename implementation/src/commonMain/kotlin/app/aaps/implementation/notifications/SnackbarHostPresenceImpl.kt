package app.aaps.implementation.notifications

import app.aaps.core.interfaces.ui.SnackbarHostPresence
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A counter of collecting snackbar hosts. See [SnackbarHostPresence] for why the count and not a
 * platform foreground check.
 *
 * Hosts come and go on the main thread, but the count is read from a background collector, so the
 * updates go through [MutableStateFlow.update], which is atomic.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SnackbarHostPresenceImpl : SnackbarHostPresence {

    private val _activeHosts = MutableStateFlow(0)
    override val activeHosts: StateFlow<Int> = _activeHosts.asStateFlow()

    override fun acquire(): AutoCloseable {
        _activeHosts.update { it + 1 }
        return Handle()
    }

    private inner class Handle : AutoCloseable {

        // A host that is cancelled while already closing must not take the count below what is
        // really there, so the release happens once and only once.
        private val closed = MutableStateFlow(false)

        override fun close() {
            if (closed.compareAndSet(expect = false, update = true))
                _activeHosts.update { it - 1 }
        }
    }
}
