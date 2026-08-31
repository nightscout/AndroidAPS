package app.aaps.di.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * What Metro guarantees about object identity across scopes.
 *
 * This exists because of the History Browser. It deliberately runs a second [SharedLeaf]-free set of
 * calculation objects over a different time window, so that browsing history cannot disturb the live
 * loop. Today it gets that by constructing them by hand. Before moving it onto a graph, the guarantee
 * it depends on has to be checked rather than assumed - a scoped object leaking between the live app
 * and the history window would mean history browsing writes into the running loop's state.
 *
 * The equivalent assumption was wrong once already on the Koin branch, where a module written as a
 * top-level `val` silently shared singletons between Koin instances. So this is measured, not read.
 */
class MetroScopingTest {

    /** Stands in for a leaf that the whole app shares, such as the logger or the database. */
    class SharedLeaf

    /** Stands in for an object the history window must have its own copy of. */
    class PerWindow

    /** Scope marker for the history window. */
    abstract class HistoryWindowScope private constructor()

    @GraphExtension(HistoryWindowScope::class)
    interface WindowGraph {

        val perWindow: PerWindow
        val sharedLeaf: SharedLeaf

        @SingleIn(HistoryWindowScope::class)
        @Provides
        fun providePerWindow(): PerWindow = PerWindow()

        @GraphExtension.Factory
        fun interface Factory {

            fun create(): WindowGraph
        }
    }

    @DependencyGraph(AppScope::class)
    interface RootGraph {

        val sharedLeaf: SharedLeaf
        val windowFactory: WindowGraph.Factory

        @SingleIn(AppScope::class)
        @Provides
        fun provideSharedLeaf(): SharedLeaf = SharedLeaf()

        @DependencyGraph.Factory
        fun interface Factory {

            fun create(): RootGraph
        }
    }

    @Test
    fun `a scoped object is the same every time within one graph`() {
        val graph = createGraphFactory<RootGraph.Factory>().create()

        assertSame(graph.sharedLeaf, graph.sharedLeaf)
    }

    @Test
    fun `two graphs built from the same factory do not share scoped objects`() {
        val first = createGraphFactory<RootGraph.Factory>().create()
        val second = createGraphFactory<RootGraph.Factory>().create()

        // If this failed, building a second graph would be a way to leak the live loop's objects into
        // the history window - and nothing in either framework would report it.
        assertNotSame(first.sharedLeaf, second.sharedLeaf)
    }

    @Test
    fun `an extension inherits its parent's scoped objects`() {
        val root = createGraphFactory<RootGraph.Factory>().create()
        val window = root.windowFactory.create()

        // The point of an extension rather than a second root graph: leaves stay shared, so the
        // history window still logs to the same logger and reads the same database.
        assertSame(root.sharedLeaf, window.sharedLeaf)
    }

    @Test
    fun `each extension gets its own copy of its own scoped objects`() {
        val root = createGraphFactory<RootGraph.Factory>().create()
        val firstWindow = root.windowFactory.create()
        val secondWindow = root.windowFactory.create()

        assertSame(firstWindow.perWindow, firstWindow.perWindow)
        // This is the History Browser guarantee: its calculation objects are its own.
        assertNotSame(firstWindow.perWindow, secondWindow.perWindow)
    }
}
