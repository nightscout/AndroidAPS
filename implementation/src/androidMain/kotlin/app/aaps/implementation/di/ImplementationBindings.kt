package app.aaps.implementation.di

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.implementation.notifications.CommonNotificationManager
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

    /**
     * The notification registry. Shared code now, with `AndroidSystemNotificationPlatform` doing the
     * Android half - together they replace the old `NotificationManagerImpl`.
     *
     * A `@Provides` rather than annotations on the class, for two reasons. The class lives in
     * commonMain and cannot carry a scope of its own, and building it registers the dismiss receiver
     * and creates the channel (through `SystemNotificationPlatform.onDismissed`), which must not
     * happen while the graph is still being assembled. A provides function runs only when something
     * first asks for a [NotificationManager].
     */
    @Provides
    @SingleIn(AppScope::class)
    fun notificationManager(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        platform: SystemNotificationPlatform,
        @ApplicationScope appScope: CoroutineScope
    ): NotificationManager = CommonNotificationManager(aapsLogger, rh, platform, appScope)
}
