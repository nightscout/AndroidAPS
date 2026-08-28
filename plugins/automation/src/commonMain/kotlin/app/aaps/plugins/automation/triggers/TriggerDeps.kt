package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.LastKnownLocation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The dependencies every [Trigger] can reach.
 *
 * A parameter object rather than twelve constructor parameters, because all twelve live on the base
 * class and would otherwise have to be threaded through all 29 subclasses and their secondary
 * constructors. [TriggerFactory] builds one and hands it out.
 *
 * Note the difference from actions: an `Action` lists its own dependencies on its own constructor, so
 * reading one tells you what it touches. A trigger does not - it can reach any of these. That is the
 * price of the smaller change, and it is the thing to revisit if triggers are ever split up.
 *
 * What it is NOT is the old `HasAndroidInjector`: this is typed, built by Dagger in one place, and
 * needs no generated members injector, so trigger code can be multiplatform.
 */
@SingleIn(AppScope::class)
class TriggerDeps @Inject constructor(
    val aapsLogger: AAPSLogger,
    val rxBus: RxBus,
    val rh: TextResolver,
    val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    val preferences: Preferences,
    val lastKnownLocation: LastKnownLocation,
    val persistenceLayer: PersistenceLayer,
    val activePlugin: ActivePlugin,
    val iobCobCalculator: IobCobCalculator,
    val glucoseStatusProvider: GlucoseStatusProvider,
    val dateUtil: DateUtil
)
