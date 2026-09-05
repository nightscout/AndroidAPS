package app.aaps.plugins.sync.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.ListenableWorker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.MetroWorkerCreator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * The bridge lives here rather than in `:app` because this module's DI qualifiers are `internal`, so
 * `:app` cannot name them. That is also the better arrangement: the module exposes its maps, never its
 * graph type, and `:app` merges them without knowing what is inside.
 * Dependencies are wrapped in [DeferredRef] rather than resolved here, for the re-entrancy reason
 * written up in `MetroGraphs`.
 */
@SingleIn(AppScope::class)
class OpenHumansMetroBridge @Inject constructor(
    private val aapsLogger: () -> AAPSLogger,
    private val rh: () -> ResourceHelper,
    private val preferences: () -> Preferences,
    private val context: () -> Context,
    private val persistenceLayer: () -> PersistenceLayer,
    private val notificationManager: () -> NotificationManager,
    private val rxBus: () -> RxBus,
    private val fabricPrivacy: () -> FabricPrivacy
) {

    private val graph: OpenHumansMetroGraph by lazy {
        createGraphFactory<OpenHumansMetroGraph.Factory>().create(
            DeferredRef { aapsLogger() },
            DeferredRef { rh() },
            DeferredRef { preferences() },
            DeferredRef { context() },
            DeferredRef { persistenceLayer() },
            DeferredRef { notificationManager() },
            DeferredRef { rxBus() },
            DeferredRef { fabricPrivacy() }
        )
    }

    /**
     * What the module contributes to the app - the maps, not the graph.
     */
    val notNsClientPlugins: Map<Int, PluginBase> get() = graph.notNsClientPlugins
    val memberInjectors: Map<KClass<*>, MembersInjector<*>> get() = graph.memberInjectors
    /**
     * View models this module contributes, in the shape metrox's factory reads. `:app` merges these
     * with the root graph's - see `AapsViewModelFactory`.
     */
    val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> get() = graph.viewModelProviders
    val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>
        get() = graph.assistedFactoryProviders
    val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
        get() = graph.manualAssistedFactoryProviders
    val workerCreators: Map<KClass<out ListenableWorker>, MetroWorkerCreator> get() = graph.workerCreators
}
