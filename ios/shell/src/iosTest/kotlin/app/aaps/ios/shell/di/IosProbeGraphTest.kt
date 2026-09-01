package app.aaps.ios.shell.di

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.logging.LTag
import app.aaps.implementation.logging.AAPSLoggerIos
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.shared.clientbindings.ClientGraphBindings

/**
 * What Metro has to do on Kotlin/Native, checked rather than displayed.
 *
 * A dependency graph is generated code. Generated code that links can still fail the first time
 * something asks it for an object, so every case here makes the graph hand something over and then
 * uses it.
 */
class IosProbeGraphTest {

    private fun graph() = createGraphFactory<IosProbeGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings)

    @Test
    fun `the graph can be created`() {
        graph()
    }

    @Test
    fun `it builds the real plugins not placeholders`() {
        val g = graph()

        // The names come from each plugin's own PluginDescription, so a plugin that was constructed
        // but not initialised would show up here as a blank.
        assertTrue(g.noSmoothing.name.isNotBlank())
        assertTrue(g.avgSmoothing.name.isNotBlank())
        assertTrue(g.exponentialSmoothing.name.isNotBlank())
        assertTrue(g.noCalibration.name.isNotBlank())
    }

    @Test
    fun `a scoped plugin is the same object every time`() {
        val g = graph()

        assertSame(g.noSmoothing, g.noSmoothing)
    }

    @Test
    fun `two graphs do not share their scoped objects`() {
        // The failure this guards against is silent. On the earlier Koin branch a module written as
        // a top level val shared singletons between graphs, which links, runs, and is still wrong.
        assertNotSame(graph().noSmoothing, graph().noSmoothing)
    }

    @Test
    fun `an injected plugin actually runs`() {
        val values = mutableListOf(
            InMemoryGlucoseValue(timestamp = 1_000L, value = 90.0),
            InMemoryGlucoseValue(timestamp = 2_000L, value = 110.0)
        )

        val smoothed = graph().avgSmoothing.smooth(values)

        assertEquals(values.size, smoothed.size)
    }

    @Test
    fun `the injected logger is the real iOS one`() {
        assertTrue(graph().logger is AAPSLoggerIos)
    }

    /**
     * Calls through the injected logger, and passes by not crashing.
     *
     * That sounds like a weak assertion and is not: the first version of `AAPSLoggerIos` segfaulted
     * here, because `NSLog` is a C varargs function and `%@` given a Kotlin String rather than an
     * NSString dereferences something that is not an object. Constructing a logger never showed it -
     * only calling through one did.
     */
    @Test
    fun `logging through the graph does not crash`() {
        val logger = graph().logger

        logger.debug(LTag.CORE, "probe")
        logger.warn(LTag.CORE, "value is {}", 42)
        logger.error(LTag.CORE, "failed", RuntimeException("boom"))

        assertTrue(true)
    }

    // ---- real AAPS implementations, not stand-ins --------------------------------------------

    @Test
    fun `the graph builds the production DateUtil`() {
        val dateUtil = graph().dateUtil

        // A real formatter behind it: the string comes from NSDateFormatter, so an empty result
        // would mean the platform side is wired but doing nothing.
        assertTrue(dateUtil.dateString(1_700_000_000_000L).isNotBlank())
        assertTrue(dateUtil.now() > 1_700_000_000_000L)
    }

    @Test
    fun `DateUtil is one object across the graph`() {
        val g = graph()

        assertSame(g.dateUtil, g.dateUtil)
    }

    @Test
    fun `the graph builds FabricPrivacy over the real preference store`() {
        val fabric = graph().fabricPrivacy

        // Reads NSUserDefaults for the enable flag, so calling it exercises the store too.
        fabric.fabricEnabled()
        fabric.logMessage("probe")
    }

    @Test
    fun `the graph opens a real database`() = runTest {
        graph().repository.use { repo ->
            // An empty database answers null rather than failing, which is what proves it opened.
            assertEquals(null, repo.getLastGlucoseValue())
        }
    }
}
