package app.aaps.plugins.automation.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.services.LastLocationDataContainer
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Hands app-wide objects from Dagger to [AutomationMetroGraph], and passes back what it contributes.
 *
 * Same shape as `OpenHumansMetroBridge`: the module owns its bridge, exposes its maps rather than its
 * graph type, and `:app` merges them without knowing what is inside. When the app-wide objects move
 * to Metro this file is deleted and the graph becomes an extension of the root graph.
 *
 * Dependencies are wrapped in [DeferredRef] rather than resolved here, for the re-entrancy reason
 * written up in `MetroGraphs`.
 */
@Singleton
class AutomationMetroBridge @Inject constructor(
    private val aapsLogger: Provider<AAPSLogger>,
    private val rh: Provider<ResourceHelper>,
    private val config: Provider<Config>,
    private val uiInteraction: Provider<UiInteraction>,
    private val rxBus: Provider<RxBus>,
    private val preferences: Provider<Preferences>,
    private val fabricPrivacy: Provider<FabricPrivacy>,
    private val notificationHolder: Provider<NotificationHolder>,
    private val lastLocationDataContainer: Provider<LastLocationDataContainer>
) {

    private val graph: AutomationMetroGraph by lazy {
        createGraphFactory<AutomationMetroGraph.Factory>().create(
            DeferredRef { aapsLogger.get() },
            DeferredRef { rh.get() },
            DeferredRef { config.get() },
            DeferredRef { uiInteraction.get() },
            DeferredRef { rxBus.get() },
            DeferredRef { preferences.get() },
            DeferredRef { fabricPrivacy.get() },
            DeferredRef { notificationHolder.get() },
            DeferredRef { lastLocationDataContainer.get() }
        )
    }

    val memberInjectors: Map<KClass<*>, MembersInjector<*>> get() = graph.memberInjectors
}
