package app.aaps.plugins.smoothing.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import app.aaps.plugins.smoothing.UnscentedKalmanFilterPlugin
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope

/**
 * The Metro counterpart of `SmoothingKoinModule` (koin-spike) and `SmoothingComponent`
 * (kotlin-inject-spike). Same four plugins, same 600-630 block, wiring in commonMain.
 *
 * Two things differ from the kotlin-inject version and both matter for the comparison:
 *
 *  - Metro is a Kotlin **compiler plugin**, not KSP. There is no annotation-processing round and no
 *    generated source file - the graph is lowered straight into IR. That is the one cost Koin avoided
 *    and kotlin-inject kept.
 *  - The map key is a real `@IntKey(600)`, the same annotation shape the Dagger module used, rather
 *    than a `Pair<Int, PluginBase>` returned by hand.
 *
 * [SingleIn] is Metro's scope marker. Stating it is still required - like kotlin-inject and unlike
 * Koin, the unscoped default would hand out a new plugin on every read.
 */
@DependencyGraph(AppScope::class)
interface SmoothingGraph {

    val plugins: Map<Int, PluginBase>

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * The bridge. Every dependency the module does not own is a checked parameter here, so a
         * missing one is a compile error rather than a crash on first use.
         */
        fun create(
            @Provides aapsLogger: AAPSLogger,
            @Provides rh: TextResolver,
            @Provides preferences: Preferences,
            @Provides persistenceLayer: PersistenceLayer
        ): SmoothingGraph
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideNoSmoothingPlugin(logger: AAPSLogger, text: TextResolver): NoSmoothingPlugin =
        NoSmoothingPlugin(logger, text)

    @SingleIn(AppScope::class)
    @Provides
    fun provideExponentialSmoothingPlugin(logger: AAPSLogger, text: TextResolver): ExponentialSmoothingPlugin =
        ExponentialSmoothingPlugin(logger, text)

    @SingleIn(AppScope::class)
    @Provides
    fun provideAvgSmoothingPlugin(logger: AAPSLogger, text: TextResolver): AvgSmoothingPlugin =
        AvgSmoothingPlugin(logger, text)

    @SingleIn(AppScope::class)
    @Provides
    fun provideUnscentedKalmanFilterPlugin(
        logger: AAPSLogger,
        text: TextResolver,
        prefs: Preferences,
        persistence: PersistenceLayer
    ): UnscentedKalmanFilterPlugin = UnscentedKalmanFilterPlugin(logger, text, prefs, persistence)

    // The 600-630 block. @IntKey is the same shape the Dagger module used.
    @Provides @IntoMap @IntKey(600) fun no(plugin: NoSmoothingPlugin): PluginBase = plugin

    @Provides @IntoMap @IntKey(610) fun exponential(plugin: ExponentialSmoothingPlugin): PluginBase = plugin

    @Provides @IntoMap @IntKey(620) fun avg(plugin: AvgSmoothingPlugin): PluginBase = plugin

    @Provides @IntoMap @IntKey(630) fun ukf(plugin: UnscentedKalmanFilterPlugin): PluginBase = plugin
}
