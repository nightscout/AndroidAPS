package app.aaps.di.metro

import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.plugins.automation.di.AutomationMetroGraph
import app.aaps.plugins.calibration.di.CalibrationGraph
import app.aaps.plugins.constraints.di.ConstraintsMetroGraph
import app.aaps.plugins.sensitivity.di.SensitivityGraph
import app.aaps.plugins.smoothing.di.SmoothingGraph
import app.aaps.plugins.source.di.SourceMetroGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes

/**
 * The one Metro root. Everything else hangs off it as a graph extension.
 *
 * Before this there were seven independent root graphs, each with its own factory restating the
 * app-wide objects it needed and its own set of one-line functions unwrapping them - around 150 lines
 * of declare-then-unwrap. Worse, it was **unsafe by construction**: two root graphs that both scope the
 * same type get one instance each, silently. `MetroScopingTest` shows exactly that - two roots built
 * from the same factory share nothing at all. It only held together because every shared object still
 * belongs to Dagger, which keeps it single. The first `@SingleIn(AppScope::class)` binding to appear in
 * two roots would have been a quiet duplicate, and in this app a duplicated calculator or command queue
 * is not a cosmetic problem.
 *
 * With one root that cannot happen: a scoped binding lives here once and every extension sees the same
 * instance. An extension that declares its own scope gets its own instances of what it scopes, which is
 * what the history browser needs and what the same test verifies.
 *
 * The app-wide objects still arrive as [DeferredRef] because they still belong to Dagger. The deferral
 * is the re-entrancy guard written up in [MetroGraphs]. When Dagger is gone the factory below goes with
 * it and these become ordinary bindings.
 */
@DependencyGraph(AppScope::class)
interface AppRootGraph {

    /** Android classes that fill their own fields. */
    val receiversGraph: AppReceiversGraph

    /** Workers, which WorkManager builds through `MetroWorkerFactory`. */
    val workersGraph: AppWorkersGraph

    /**
     * Opens one history browsing window. A factory rather than an accessor, because each window is a
     * separate scope with its own calculation objects - see [HistoryWindowGraph].
     */
    val historyWindowFactory: HistoryWindowGraph.Factory

    /**
     * Feature modules, as extensions rather than roots of their own.
     *
     * Each of these used to be a second `@DependencyGraph(AppScope::class)`, built by `MetroGraphs`
     * with its own list of leaves. That was safe only by accident: two graphs both declaring `AppScope`
     * get a separate copy of anything scoped there, and nothing reports it. As extensions they share
     * this graph's bindings instead of restating them, so their factories take no arguments at all.
     */
    val smoothingGraph: SmoothingGraph
    val sensitivityGraph: SensitivityGraph
    val calibrationGraph: CalibrationGraph
    val coreObjectsGraph: CoreObjectsGraph

    val sourceGraph: SourceMetroGraph
    val automationGraph: AutomationMetroGraph
    val constraintsGraph: ConstraintsMetroGraph

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * One parameter, not thirty-five. [AapsLeaves] carries everything Dagger still owns, and its
         * @Provides functions are called only when something needs that type - so the deferral that
         * DeferredRef used to do by hand is now just the shape of a binding container.
         */
        fun create(@Includes leaves: AapsLeaves): AppRootGraph
    }
}
