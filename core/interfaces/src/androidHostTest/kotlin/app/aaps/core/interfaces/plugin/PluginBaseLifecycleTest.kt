package app.aaps.core.interfaces.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.TextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.time.Duration.Companion.seconds

/**
 * That enabling and disabling a plugin can be waited for.
 *
 * [PluginBase.setPluginEnabled] sets the state flag straight away but only *schedules*
 * [PluginBase.onStart] / [PluginBase.onStop] on the plugin's own scope. That is fine on the startup
 * path, and not fine when imported settings are applied to a running app: the caller waits for an idle
 * pump, applies, and then lets commands flow again - which it must not do while a pump driver is still
 * being torn down. The job is returned so that caller can wait for it.
 */
class PluginBaseLifecycleTest {

    private class TestPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver
    ) : PluginBase(PluginDescription().mainType(PluginType.GENERAL), aapsLogger, rh) {

        // Held open until a test lets the phase through, so "scheduled" and "finished" can be told apart.
        val startGate = CompletableDeferred<Unit>()
        val stopGate = CompletableDeferred<Unit>()
        var started = false
        var stopped = false

        override suspend fun onStart() {
            startGate.await()
            started = true
        }

        override suspend fun onStop() {
            stopGate.await()
            stopped = true
        }
    }

    private fun plugin() = TestPlugin(mock<AAPSLogger>(), mock<TextResolver>())

    /** Enables the plugin and waits for it, so a test can start from a genuinely started plugin. */
    private suspend fun TestPlugin.enableAndAwait() {
        startGate.complete(Unit)
        withTimeout(5.seconds) { setPluginEnabledAwaiting(PluginType.GENERAL, true) }
    }

    @Test
    fun `enabling returns the job that runs onStart`() = runBlocking {
        val sut = plugin()

        val job = sut.setPluginEnabled(PluginType.GENERAL, true)

        assertThat(job).isNotNull()
        assertThat(sut.started).isFalse()   // scheduled, not run
        sut.startGate.complete(Unit)
        withTimeout(5.seconds) { job!!.join() }
        assertThat(sut.started).isTrue()
    }

    @Test
    fun `disabling returns the job that runs onStop`() = runBlocking {
        val sut = plugin()
        sut.enableAndAwait()

        val job = sut.setPluginEnabled(PluginType.GENERAL, false)

        assertThat(job).isNotNull()
        assertThat(sut.stopped).isFalse()   // scheduled, not run
        sut.stopGate.complete(Unit)
        withTimeout(5.seconds) { job!!.join() }
        assertThat(sut.stopped).isTrue()
    }

    /** Nothing to wait for when the state did not change, so there is no job to return. */
    @Test
    fun `enabling a plugin that is already enabled returns no job`() = runBlocking {
        val sut = plugin()
        sut.enableAndAwait()

        assertThat(sut.setPluginEnabled(PluginType.GENERAL, true)).isNull()
    }

    @Test
    fun `disabling a plugin that is already disabled returns no job`() {
        val sut = plugin()

        assertThat(sut.setPluginEnabled(PluginType.GENERAL, false)).isNull()
    }

    /** The whole point: the awaiting form does not come back until onStart has finished. */
    @Test
    fun `the awaiting form waits for onStart`() = runBlocking {
        val sut = plugin()

        val enabling = async { sut.setPluginEnabledAwaiting(PluginType.GENERAL, true) }
        delay(200)
        assertThat(enabling.isCompleted).isFalse()

        sut.startGate.complete(Unit)
        withTimeout(5.seconds) { enabling.await() }
        assertThat(sut.started).isTrue()
    }

    /** ...and returns straight away when there was nothing to start. */
    @Test
    fun `the awaiting form returns at once when nothing changed`() = runBlocking {
        val sut = plugin()

        withTimeout(5.seconds) { sut.setPluginEnabledAwaiting(PluginType.GENERAL, false) }

        assertThat(sut.started).isFalse()
    }
}
