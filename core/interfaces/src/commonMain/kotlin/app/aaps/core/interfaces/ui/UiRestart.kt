package app.aaps.core.interfaces.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks the running UI to rebuild itself, from code that cannot reach the shell.
 *
 * `onRecreateActivity` already exists for this, but it is a lambda handed down inside each shell's
 * composition - so a view model reached through navigation, like the import one, has no way to call
 * it. This is the same request from the other direction: anything with the singleton can ask, and
 * each shell answers the way its platform can.
 *
 * ## Why a rebuild is needed at all
 *
 * Two callers, both of which change what already-composed screens are showing rather than what the
 * app is doing:
 *
 * - **After an import.** The settings are applied to the running app, but a screen composed before
 *   that still shows the values it read. Without a rebuild the import looks as though it failed.
 * - **After a language change.** `TextRefValueRegistry` is plain state, so Compose has no idea the
 *   text changed. Android instead reloads a `Context` and recreates the activity, because there the
 *   locale lives in `Resources`.
 *
 * ## What each platform does with it
 *
 * Android recreates the activity, which is the only thing that re-runs `attachBaseContext` and so
 * the only thing that can change the locale `Resources` resolves against. iOS and the desktop key
 * the root on [signal], which rebuilds the composition without a process restart - and on iOS that
 * matters, because it may not restart at all.
 *
 * A counter rather than an event, so a shell that was not collecting at the moment of the request
 * still sees a value it has not handled yet.
 */
interface UiRestart {

    /** Increases every time a rebuild is asked for. */
    val signal: StateFlow<Long>

    /** Asks for a rebuild. Safe to call when nothing is listening; the count still moves. */
    fun request()
}

/**
 * The one implementation. Not a platform seam - only the answering side differs, and that is the
 * shell's collector.
 */
class UiRestartImpl : UiRestart {

    private val _signal = MutableStateFlow(0L)
    override val signal: StateFlow<Long> = _signal.asStateFlow()

    override fun request() {
        _signal.value += 1
    }
}
