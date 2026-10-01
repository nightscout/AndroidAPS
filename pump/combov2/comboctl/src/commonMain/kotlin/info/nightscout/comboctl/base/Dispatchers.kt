package info.nightscout.comboctl.base

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// Dispatcher that enforces sequential execution of Combo IO, disallowing parallelism.
// This is important, since parallel IO is not supported by the Combo and only causes IO errors.
//
// This is a *dedicated* single thread rather than Dispatchers.Default.limitedParallelism(1):
// limitedParallelism only caps concurrency, it still dispatches onto the shared, CPU-bound
// Dispatchers.Default pool. When that pool is starved (e.g. CI running the unit tests pinned to
// a few cores alongside emulators), the transport send/receive handshake could not get a thread
// turn to deliver an RT button-press confirmation, so the loop's confirmation.await() never
// resumed and the test watchdog fired (PumpIOTest "Test run timeout reached"). A dedicated thread
// is isolated from Default's load, so serialized IO always makes progress. The thread is a daemon
// so it never keeps the JVM alive.
internal val sequencedDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "comboctl-sequenced-io").apply { isDaemon = true }
    }.asCoroutineDispatcher()
