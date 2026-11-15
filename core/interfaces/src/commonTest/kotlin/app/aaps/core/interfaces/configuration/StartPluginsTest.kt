package app.aaps.core.interfaces.configuration

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The announcement that starts the first calculation.
 *
 * The order is the whole point: `IobCobCalculatorPlugin` subscribes in its own `onStart`, so an
 * event sent before the start jobs finish reaches nobody and the graph stays empty until the next
 * glucose reading (nightscout/AndroidAPS#5485).
 */
class StartPluginsTest {

    private class RecordingBus : RxBus {

        val sent = mutableListOf<Event>()
        override fun send(event: Event) {
            sent += event
        }

        override fun <T : Event> toFlow(eventType: KClass<T>): Flow<T> = emptyFlow()
    }

    private class RecordingLogger : AAPSLogger {

        val warnings = mutableListOf<String>()
        override fun debug(message: String) = Unit
        override fun debug(enable: Boolean, tag: LTag, message: String) = Unit
        override fun debug(tag: LTag, message: String) = Unit
        override fun debug(tag: LTag, accessor: () -> String) = Unit
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) = Unit
        override fun warn(tag: LTag, message: String) {
            warnings += message
        }

        override fun warn(tag: LTag, format: String, vararg arguments: Any?) = Unit
        override fun info(tag: LTag, message: String) = Unit
        override fun info(tag: LTag, format: String, vararg arguments: Any?) = Unit
        override fun error(tag: LTag, message: String) = Unit
        override fun error(tag: LTag, message: String, throwable: Throwable) = Unit
        override fun error(tag: LTag, format: String, vararg arguments: Any?) = Unit
        override fun error(message: String) = Unit
        override fun error(message: String, throwable: Throwable) = Unit
        override fun error(format: String, vararg arguments: Any?) = Unit
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = Unit
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = Unit
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = Unit
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = Unit
    }

    /** Only [initialize] is used here; the rest of the interface is not part of this behaviour. */
    private class FakeConfigBuilder(private val jobs: List<Job>) : ConfigBuilder {

        var initializeCalls = 0
        override val syncedSelectionTypes: List<PluginType> get() = emptyList()
        override val activeSelectionChanges: Flow<Unit> get() = emptyFlow()
        override fun initialize(): List<Job> {
            initializeCalls++
            return jobs
        }

        override suspend fun applyConfiguration() = Unit
        override fun storeSettings(from: String) = Unit
        override fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType) = Unit
        override fun requestPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType): String? = null
        override fun confirmPumpPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType) = Unit
        override fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType) = Unit
        override fun exitApp(from: String, source: Sources, launchAgain: Boolean) = Unit
    }

    @Test
    fun `the app is announced ready only after the plugins have started`() = runTest {
        val bus = RecordingBus()
        val logger = RecordingLogger()
        val starting = CompletableDeferred<Unit>()
        val startJob = launch { starting.await() }

        val announcement = FakeConfigBuilder(listOf(startJob)).initializeAndAnnounceReady(this, bus, logger)

        advanceTimeBy(5.seconds)
        assertTrue(bus.sent.isEmpty(), "the event was sent while the plugins were still starting, so nobody is subscribed yet")

        starting.complete(Unit)
        announcement.join()
        assertEquals(1, bus.sent.filterIsInstance<EventAppInitialized>().size)
        assertTrue(logger.warnings.isEmpty(), "nothing timed out, so nothing should warn")
    }

    @Test
    fun `a plugin that never starts does not hold the calculation back for ever`() = runTest {
        val bus = RecordingBus()
        val logger = RecordingLogger()
        // Never completed, like a driver whose onStart does not settle.
        val stuck = launch { CompletableDeferred<Unit>().await() }

        val announcement = FakeConfigBuilder(listOf(stuck)).initializeAndAnnounceReady(this, bus, logger, wait = 30.seconds)

        advanceTimeBy(29.seconds)
        assertTrue(bus.sent.isEmpty(), "the wait is not over yet")

        advanceTimeBy(2.seconds)
        assertEquals(1, bus.sent.filterIsInstance<EventAppInitialized>().size, "the event must be sent anyway, or nothing is ever calculated")
        assertTrue(logger.warnings.any { it.contains("did not finish starting") }, "a timed out start has to say so")
        stuck.cancel()
        announcement.join()
    }

    @Test
    fun `the plugins are started exactly once`() = runTest {
        val bus = RecordingBus()
        val builder = FakeConfigBuilder(emptyList())

        builder.initializeAndAnnounceReady(this, bus, RecordingLogger()).join()

        assertEquals(1, builder.initializeCalls)
        assertEquals(1, bus.sent.size)
    }
}
