package app.aaps.plugins.calibration.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.calibration.LinearCalibrationPlugin
import app.aaps.plugins.calibration.NoCalibrationPlugin
import app.aaps.plugins.calibration.compose.CalibrationViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Scope marker for this module's calibration objects.
 *
 * The graph used to be a second root on `AppScope`, which was unsafe: two graphs declaring one scope
 * each get their own copy of anything scoped there, silently. Now it is an extension with a scope of
 * its own, so `AppScope` means exactly one graph.
 */
abstract class CalibrationScope private constructor()

/**
 * The Metro counterpart of `CalibrationPluginsModule`, the Dagger module that used to live in `:app`.
 * Same two plugins, same 700-710 block, wiring in commonMain so it also compiles for iOS.
 *
 * This module carries two things the smoothing module does not, and both are the reason it is in the
 * head-to-head comparison at all:
 *
 *  - A **view model** ([CalibrationViewModel]) handed out as a graph accessor. It stands in for the 77
 *    `@HiltViewModel` classes in the tree, which are Android only and so cannot exist in common code.
 *  - **Assisted injection** ([AssistedProbe]), a caller supplied value mixed with graph dependencies.
 *    It stands in for `GraphViewModel` in `:ui`, which is `@AssistedInject` with an `@AssistedFactory`.
 *
 * On scoping: Metro is unscoped by default, so [SingleIn] has to be stated on every provider that was
 * `@Singleton` in the Dagger module. Without it every read of the accessor would build a new plugin,
 * and a plugin holds state (its coroutine scope, its enabled flag). The view model is the deliberate
 * exception - it belongs to one screen, so a new one per read is what we want.
 *
 * What the Dagger module said, kept verbatim so nothing is dropped from a build variant:
 *  - `@Provides @Singleton` on both plugin providers
 *  - `@Binds @AllConfigs @IntoMap @IntKey(700)` for NoCalibrationPlugin
 *  - `@Binds @AllConfigs @IntoMap @IntKey(710)` for LinearCalibrationPlugin
 *
 * `@AllConfigs` is an `:app` side qualifier and it does not appear here. The graph hands out a plain
 * `Map<Int, PluginBase>`; re-qualifying that map into the global `@AllConfigs` map stays in `:app`,
 * exactly as it does for the smoothing graph.
 */
@GraphExtension(CalibrationScope::class)
interface CalibrationGraph {

    val plugins: Map<Int, PluginBase>

    /**
     * Not scoped on purpose. A view model belongs to one screen, so every read must build a new one.
     * On Android the screen still owns the lifetime through `viewModel { }`; the graph only supplies
     * a constructed instance with its dependencies already filled in.
     */
    val calibrationViewModel: CalibrationViewModel

    /** The assisted factory itself is the binding you take from the graph, not the probe. */
    val assistedProbeFactory: AssistedProbe.Factory

    @SingleIn(CalibrationScope::class)
    @Provides
    fun provideNoCalibrationPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver
    ): NoCalibrationPlugin = NoCalibrationPlugin(
        aapsLogger = aapsLogger,
        rh = rh
    )

    @SingleIn(CalibrationScope::class)
    @Provides
    fun provideLinearCalibrationPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        notificationManager: NotificationManager,
        glucoseStatusProvider: GlucoseStatusProvider,
        rxBus: RxBus,
        profileUtil: ProfileUtil
    ): LinearCalibrationPlugin = LinearCalibrationPlugin(
        aapsLogger = aapsLogger,
        rh = rh,
        dateUtil = dateUtil,
        persistenceLayer = persistenceLayer,
        notificationManager = notificationManager,
        glucoseStatusProvider = glucoseStatusProvider,
        rxBus = rxBus,
        profileUtil = profileUtil
    )

    /**
     * No [SingleIn] here. See [calibrationViewModel].
     */
    @Provides
    fun provideCalibrationViewModel(
        persistenceLayer: PersistenceLayer,
        profileUtil: ProfileUtil,
        aapsLogger: AAPSLogger,
        dateUtil: DateUtil
    ): CalibrationViewModel = CalibrationViewModel(
        persistenceLayer = persistenceLayer,
        profileUtil = profileUtil,
        aapsLogger = aapsLogger,
        dateUtil = dateUtil
    )

    // The 700-710 block. @IntKey is the same shape the Dagger module used.
    @Provides @IntoMap @IntKey(700) fun no(plugin: NoCalibrationPlugin): PluginBase = plugin

    @Provides @IntoMap @IntKey(710) fun linear(plugin: LinearCalibrationPlugin): PluginBase = plugin
}

/**
 * Spike probe. Not production code and not called by the app - it exists only so the DI comparison has
 * a working assisted injection case in commonMain, the same shape as `GraphViewModel` in `:ui`
 * (`@AssistedInject` constructor plus a nested `@AssistedFactory`).
 *
 * Metro spells it like Dagger, with two differences worth writing down:
 *  - The annotation sits on the **class**, not on a `constructor` keyword: `@AssistedInject class ...`.
 *  - Assisted parameters are matched by **name**, not by position or by a custom identifier string, so
 *    `label` in the constructor and `label` in [Factory.create] have to keep the same name. Renaming
 *    one of them is a compile error, which is the behaviour we want.
 *
 * The class is deliberately not scoped. An assisted type cannot be a singleton - its value depends on
 * what the caller passed in.
 */
@AssistedInject
class AssistedProbe(
    @Assisted val label: String,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) {

    @AssistedFactory
    fun interface Factory {

        fun create(label: String): AssistedProbe
    }

    fun describe(): String = label + "@" + dateUtil.now()
}
