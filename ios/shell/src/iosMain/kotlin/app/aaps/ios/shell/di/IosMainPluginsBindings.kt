package app.aaps.ios.shell.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The live loop's calculator, the iOS counterpart of `MainPluginsBindings` in `:app`.
 *
 * Real AAPS code, not a placeholder: `IobCobCalculatorPlugin` is arithmetic over the treatment
 * history and has been portable all along - it was only ever missing because nothing on iOS said
 * how to build it.
 *
 * It is stated here rather than annotated for the same reason as on Android: the class is built
 * twice on purpose, once for the live loop and once for the history browser at its own scope, so a
 * `@SingleIn(AppScope::class)` on the class itself would make which one a window sees stop being
 * obvious. (iOS has no history window graph yet, so only the live one exists today.)
 *
 * The calculator and the cache need each other; [Provider] defers the lookup so the graph accepts
 * what a direct reference would reject.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object IosMainPluginsBindings {

    @Suppress("LongParameterList")
    @Provides
    @SingleIn(AppScope::class)
    fun iobCobCalculatorPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences,
        rh: TextResolver,
        profileFunction: ProfileFunction,
        activePlugin: ActivePlugin,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        overviewData: OverviewData,
        calculationWorkflow: CalculationWorkflow,
        decimalFormatter: DecimalFormatter,
        processedTbrEbData: ProcessedTbrEbData,
        signals: CalculationSignalsEmitter,
        cache: Provider<OverviewDataCache>
    ): IobCobCalculatorPlugin = IobCobCalculatorPlugin(
        aapsLogger, rxBus, preferences, rh, profileFunction, activePlugin, dateUtil, persistenceLayer,
        overviewData, calculationWorkflow, decimalFormatter, processedTbrEbData, signals
    ) { cache() }

    @Provides
    fun iobCobCalculator(plugin: IobCobCalculatorPlugin): IobCobCalculator = plugin

    @Provides
    @IntoMap
    @IntKey(10)
    fun iobCobCalculatorEntry(plugin: IobCobCalculatorPlugin): PluginBase = plugin

    /**
     * The objectives, now that they are shared code rather than androidMain.
     *
     * They contribute themselves into a map keyed by their number; `ObjectivesPlugin` wants an
     * ordered list, and that key is the order. Added from the Windows side together with the move,
     * so this graph keeps building - the same two providers `:app` and the desktop shell have.
     */
    @Provides
    fun objectivesList(objectives: Map<Int, Objective>): List<Objective> =
        objectives.toList().sortedBy { it.first }.map { it.second }

    @Provides
    fun objectives(plugin: ObjectivesPlugin): Objectives = plugin
}
