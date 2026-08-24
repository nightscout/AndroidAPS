package app.aaps.plugins.source.di

import android.content.Context
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.plugins.source.AidexPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.NotificationReaderPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.instara.InstaraPlugin
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Hands what [SourceMetroGraph] needs from Dagger, and passes its two maps back to `:app`.
 *
 * The plugins are passed in rather than built here for the re-entrancy reason in `MetroGraphs`: a
 * source plugin leads to the plugin list, which asks the Metro graphs.
 */
@Singleton
class SourceMetroBridge @Inject constructor(
    private val aapsLogger: Provider<AAPSLogger>,
    private val fabricPrivacy: Provider<FabricPrivacy>,
    private val persistenceLayer: Provider<PersistenceLayer>,
    private val preferences: Provider<Preferences>,
    private val dateUtil: Provider<DateUtil>,
    private val profileUtil: Provider<ProfileUtil>,
    private val profileFunction: Provider<ProfileFunction>,
    private val rxBus: Provider<RxBus>,
    private val context: Provider<Context>,
    private val dataInbox: Provider<DataInbox>,
    private val notificationManager: Provider<NotificationManager>,
    private val virtualPump: Provider<VirtualPump>,
    private val aidex: Provider<AidexPlugin>,
    private val dexcom: Provider<DexcomPlugin>,
    private val glimp: Provider<GlimpPlugin>,
    private val instara: Provider<InstaraPlugin>,
    private val mm640g: Provider<MM640gPlugin>,
    private val patchedSiApp: Provider<PatchedSiAppPlugin>,
    private val patchedSinoApp: Provider<PatchedSinoAppPlugin>,
    private val poctech: Provider<PoctechPlugin>,
    private val syai: Provider<SyaiPlugin>,
    private val tomato: Provider<TomatoPlugin>,
    private val xdrip: Provider<XdripSourcePlugin>,
    private val notificationReader: Provider<NotificationReaderPlugin>
) {

    private val graph: SourceMetroGraph by lazy {
        createGraphFactory<SourceMetroGraph.Factory>().create(
            DeferredRef { aapsLogger.get() },
            DeferredRef { fabricPrivacy.get() },
            DeferredRef { persistenceLayer.get() },
            DeferredRef { preferences.get() },
            DeferredRef { dateUtil.get() },
            DeferredRef { profileUtil.get() },
            DeferredRef { profileFunction.get() },
            DeferredRef { rxBus.get() },
            DeferredRef { context.get() },
            DeferredRef { dataInbox.get() },
            DeferredRef { notificationManager.get() },
            DeferredRef { virtualPump.get() },
            DeferredRef { aidex.get() },
            DeferredRef { dexcom.get() },
            DeferredRef { glimp.get() },
            DeferredRef { instara.get() },
            DeferredRef { mm640g.get() },
            DeferredRef { patchedSiApp.get() },
            DeferredRef { patchedSinoApp.get() },
            DeferredRef { poctech.get() },
            DeferredRef { syai.get() },
            DeferredRef { tomato.get() },
            DeferredRef { xdrip.get() },
            DeferredRef { notificationReader.get() }
        )
    }

    val workerCreators: Map<KClass<*>, MetroWorkerCreator> get() = graph.workerCreators
    val memberInjectors: Map<KClass<*>, MembersInjector<*>> get() = graph.memberInjectors
}
