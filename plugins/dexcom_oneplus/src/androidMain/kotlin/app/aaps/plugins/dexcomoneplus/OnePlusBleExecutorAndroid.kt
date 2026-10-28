package app.aaps.plugins.dexcomoneplus

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal actual fun onePlusBleExecutor(): OnePlusBleExecutor = OnePlusAndroidBleExecutor()

/**
 * The single-threaded executor the driver has always used, behind [OnePlusBleExecutor].
 *
 * Daemon, so a stuck session cannot keep the process alive, and named so a thread dump says which
 * thread is blocked in the handshake.
 */
private class OnePlusAndroidBleExecutor : OnePlusBleExecutor {

    private val delegate: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OnePlusBleExecutor").apply { isDaemon = true }
    }

    /** Throws `RejectedExecutionException` after [shutdownNow], exactly as before. */
    override fun execute(block: () -> Unit) {
        delegate.execute(block)
    }

    /** Also interrupts the running cycle, which is what cuts `onePlusSleep` short. */
    override fun shutdownNow() {
        delegate.shutdownNow()
    }
}
