package app.aaps.ios.shell.di

import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.shared.clientbindings.ClientGraphBindings
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * That a history window calculates on its own objects, not the running loop's.
 *
 * This is the one property the whole `@GraphExtension` arrangement exists for, and it is invisible
 * from the code that uses it: `HistoryScope` hands back an `IobCobCalculator` and an `OverviewData`
 * either way, so a wiring mistake that shared the app's singletons would compile, run, and look
 * correct on screen. What it would actually do is let browsing yesterday rewrite the state the loop
 * is calculating with right now.
 *
 * Asserting on identity rather than behaviour is deliberate: behaviour would only differ once both
 * were mid-calculation, which is exactly when it is too late to notice.
 */
class IosHistoryWindowScopingTest {

    private val graph = createGraphFactory<IosAppGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings)

    @Test
    fun `a window does not share the app's calculation objects`() {
        val window = graph.historyWindowFactory.create()

        assertNotSame(graph.overviewDataCache, window.cache, "the window must not read the app's cache")
        assertNotSame<Any>(graph.iobCobCalculator, window.iobCobCalculator, "the window must not use the loop's calculator")
    }

    @Test
    fun `two windows do not share with each other`() {
        val first = graph.historyWindowFactory.create()
        val second = graph.historyWindowFactory.create()

        assertNotSame(first.cache, second.cache)
        assertNotSame(first.iobCobCalculator, second.iobCobCalculator)
        assertNotSame(first.overviewData, second.overviewData)
        assertNotSame(first.signals, second.signals)
    }

    /** Scoped within one window, or its cache and its calculator would be talking past each other. */
    @Test
    fun `one window hands out the same objects every time`() {
        val window = graph.historyWindowFactory.create()

        assertSame(window.cache, window.cache)
        assertSame(window.iobCobCalculator, window.iobCobCalculator)
        assertSame(window.overviewData, window.overviewData)
    }

    /**
     * The app's own objects stay shared, which is the other half of the arrangement - the extension
     * must not have turned the loop's singletons into per-injection instances.
     */
    @Test
    fun `the app's own calculation objects are still singletons`() {
        assertSame(graph.iobCobCalculator, graph.iobCobCalculator)
        assertSame(graph.overviewDataCache, graph.overviewDataCache)
    }
}
