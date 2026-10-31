package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.shared.clientbindings.ClientGraphBindings
import app.aaps.ui.compose.overview.OverviewDataCacheImpl
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * That a history window calculates on its own objects, not the running loop's.
 *
 * The desktop copy of `IosHistoryWindowScopingTest`, because the window graph is shared but each
 * shell wires it into its own graph, and a wiring mistake in one shell is not seen by the other's test.
 *
 * It matters because the mistake is invisible from the code that uses it: `HistoryScope` hands back an
 * `IobCobCalculator` and an `OverviewData` either way, so a graph that shared the app's singletons
 * would compile, run, and look correct on screen. What it would really do is let browsing yesterday
 * rewrite the state the loop is calculating with right now.
 *
 * Asserting on identity rather than behaviour is deliberate: behaviour would only differ once both
 * were mid-calculation, which is exactly when it is too late to notice.
 *
 * The graph is the real one. The build points `user.home` at a folder under `build`, so the
 * preferences, the log and the database it opens are not the developer's own.
 */
class DesktopHistoryWindowScopingTest {

    private val graph = createGraphFactory<DesktopAppGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings)

    /** The History Browser used to get a placeholder that threw on first use, which crashed the window. */
    @Test
    fun `the history screen gets a window of its own`() {
        val scope = graph.historyScope

        assertNotSame(graph.overviewDataCache, scope.cache, "the history screen must not read the app's cache")
        assertNotSame<Any>(graph.iobCobCalculator, scope.iobCobCalculator, "the history screen must not use the loop's calculator")
        assertNotSame(graph.overviewData, scope.overviewData, "the history screen must not move the app's time range")
        assertNotSame(graph.calculationSignals, scope.signals, "the history screen must not report on the app's signals")
    }

    @Test
    fun `a window does not share the app's calculation objects`() {
        val window = graph.historyWindowFactory.create()

        assertNotSame(graph.overviewDataCache, window.cache, "the window must not read the app's cache")
        assertNotSame<Any>(graph.iobCobCalculator, window.iobCobCalculator, "the window must not use the loop's calculator")
        assertNotSame(graph.overviewData, window.overviewData, "the window must not move the app's time range")
        assertNotSame(graph.calculationSignals, window.signals, "the window must not report on the app's signals")
    }

    /**
     * The window's objects hold each other, not the app's.
     *
     * The tests above only prove the window has objects of its own. This one proves they were built
     * from each other: a calculator that was handed the app's `OverviewData` would pass every check
     * above and still write the history time range into the live overview. Metro decides this when a
     * window's own binding hides the parent's one for the same type, and nothing else would notice.
     */
    @Test
    fun `a window's objects are wired to each other`() {
        val window = graph.historyWindowFactory.create()
        val cache = window.cache as OverviewDataCacheImpl

        assertSame(window.iobCobCalculator, cache.iobCobCalculatorProvider(), "the window's cache must read the window's calculator")
        assertSame<Any>(window.signals, cache.signals, "the window's cache must listen to the window's signals")

        val calculator = window.iobCobCalculator
        assertSame<Any?>(window.overviewData, calculator.field("overviewData"), "the window's calculator must use the window's overview data")
        assertSame<Any?>(window.signals, calculator.field("signals"), "the window's calculator must report on the window's signals")
        assertSame<Any?>(window.cache, calculator.cacheItReads(), "the window's calculator must write into the window's cache")
    }

    /**
     * The same checks on the app's own objects.
     *
     * Partly as the other half of the arrangement, and partly to prove the field reads work: if they
     * read the wrong thing, this test fails even when the window test above would pass.
     */
    @Test
    fun `the app's objects are wired to each other`() {
        val cache = graph.overviewDataCache as OverviewDataCacheImpl

        assertSame(graph.iobCobCalculator, cache.iobCobCalculatorProvider())
        assertSame<Any>(graph.calculationSignals, cache.signals)

        val calculator = graph.iobCobCalculator
        assertSame<Any?>(graph.overviewData, calculator.field("overviewData"))
        assertSame<Any?>(graph.calculationSignals, calculator.field("signals"))
        assertSame<Any?>(graph.overviewDataCache, calculator.cacheItReads())
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
        assertSame(graph.overviewData, graph.overviewData)
        assertSame(graph.calculationSignals, graph.calculationSignals)
        assertSame(graph.historyScope, graph.historyScope)
    }

    /**
     * Reads one of the calculator's private constructor properties.
     *
     * Reflection, because `IobCobCalculatorPlugin` keeps them private, and they are exactly the part a
     * wiring mistake would get wrong. A renamed field fails here with `NoSuchFieldException`, so the
     * test cannot pass by checking nothing.
     */
    private fun IobCobCalculator.field(name: String): Any? =
        IobCobCalculatorPlugin::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this as IobCobCalculatorPlugin)

    /** The cache the calculator writes into. It holds a function rather than the cache, to break a cycle. */
    private fun IobCobCalculator.cacheItReads(): Any? = (field("cache") as () -> Any?)()
}
