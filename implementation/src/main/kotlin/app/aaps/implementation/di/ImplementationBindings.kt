package app.aaps.implementation.di

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.BolusProgressData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

/**
 * Metro bindings for classes in this module that cannot carry the annotations themselves.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ImplementationBindings {

    /**
     * [BolusProgressData] is a plain class rather than an `@Inject constructor` one, because
     * `javax.inject` cannot be used from code that is meant to reach commonMain. Providing it here
     * keeps the graph identical: same singleton scope, same application-scoped CoroutineScope.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun bolusProgressData(
        ch: ConcentrationHelper,
        @ApplicationScope appScope: CoroutineScope
    ): BolusProgressData = BolusProgressData(ch, appScope)
}
