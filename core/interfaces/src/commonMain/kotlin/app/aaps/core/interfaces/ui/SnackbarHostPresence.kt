package app.aaps.core.interfaces.ui

import kotlinx.coroutines.flow.StateFlow

/**
 * Counts the snackbar hosts that are collecting events right now.
 *
 * `EventShowSnackbar` is surfaced in two different ways, and exactly one of them should run for a
 * given message:
 *
 *  - A visible `GlobalSnackbarHost` shows it as a snackbar. The host only collects while its
 *    lifecycle is at least STARTED, so it stops on its own when the UI goes away.
 *  - When no host is collecting, a background fallback turns the message into a system
 *    notification, so it is not lost.
 *
 * The fallback needs to know which case it is in. Asking the platform ("is the process in
 * foreground?") is not the same question and needs a different answer on every target. Asking the
 * hosts themselves works everywhere and cannot drift from what the hosts actually do: a host holds
 * a [acquire] handle for exactly as long as it collects.
 */
interface SnackbarHostPresence {

    /**
     * How many hosts are collecting. Zero means a message would be seen by nobody, which is when
     * the fallback should step in.
     */
    val activeHosts: StateFlow<Int>

    /**
     * Marks one host as collecting. Close the returned handle when it stops - `use { }` around the
     * collect loop does that on cancellation too.
     */
    fun acquire(): AutoCloseable
}
