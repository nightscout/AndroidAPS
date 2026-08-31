package app.aaps.di

import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Gives each test method a fresh Metro graph, which `HiltAndroidRule` used to do implicitly.
 *
 * Hilt built a new component per test method, so every test got new instances of everything. Without
 * Hilt the application owns one graph, and this run shares one process (there is no Android Test
 * Orchestrator), so the graph would otherwise be built once for the entire suite.
 *
 * That difference is not academic. `RfcommTransport` and `BleTransport` decide between the in-tree pump
 * emulator and the real transport by reading `config.isEnabled` **once, at construction**. The Dana
 * tests select a different pump variant per test method; with one shared graph, every test after the
 * first would quietly drive the first one's transport and assert against the wrong pump.
 *
 * `starting`, not `finished`: a test sets its `EmulatedOptions` inside its own body, and the reset has
 * to happen before that, not after.
 */
class ResetGraphRule : TestWatcher() {

    override fun starting(description: Description) {
        ApplicationProvider.getApplicationContext<BaseTestApp>().resetGraph()
        EmulatedOptions.enabled = emptySet()
    }
}
