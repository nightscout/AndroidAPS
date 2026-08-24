package app.aaps.plugins.constraints.di

import android.content.Context
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.MetroViewModelCreator
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import dev.zacsweers.metro.createGraphFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Hands what [ConstraintsMetroGraph] needs from Dagger, and passes its three plugin buckets back.
 *
 * Note what is no longer in this list. Five of the seven plugins used to be passed in already built;
 * Metro builds them now, so all this supplies for them is app-wide leaves. Only [ObjectivesPlugin] and
 * [SignatureVerifierPlugin] are still handed over, for the reasons in the graph's own documentation.
 * When those two follow, this class supplies leaves only - and once the leaves are Metro-owned it goes
 * away entirely.
 *
 * The module owns its bridge because it is a plain Android library. A multiplatform module cannot do
 * this - Dagger has no Java step there - so those keep their bridge in `:app`.
 */
@Singleton
class ConstraintsMetroBridge @Inject constructor(
    private val signatureVerifier: Provider<SignatureVerifierPlugin>,
    private val objectives: Provider<ObjectivesPlugin>,
    private val aapsLogger: Provider<AAPSLogger>,
    private val rxBus: Provider<RxBus>,
    private val rh: Provider<ResourceHelper>,
    private val dateUtil: Provider<DateUtil>,
    private val sntpClient: Provider<SntpClient>,
    private val receiverStatusStore: Provider<ReceiverStatusStore>,
    private val uel: Provider<UserEntryLogger>,
    private val preferences: Provider<Preferences>,
    private val constraintsChecker: Provider<ConstraintsChecker>,
    private val activePlugin: Provider<ActivePlugin>,
    private val hardLimits: Provider<HardLimits>,
    private val config: Provider<Config>,
    private val persistenceLayer: Provider<PersistenceLayer>,
    private val notificationManager: Provider<NotificationManager>,
    private val decimalFormatter: Provider<DecimalFormatter>,
    private val versionCheckerUtils: Provider<VersionCheckerUtils>,
    private val loop: Provider<Loop>,
    private val profileFunction: Provider<ProfileFunction>,
    private val iobCobCalculator: Provider<IobCobCalculator>,
    private val context: Provider<Context>
) {

    private val graph: ConstraintsMetroGraph by lazy {
        createGraphFactory<ConstraintsMetroGraph.Factory>().create(
            DeferredRef { signatureVerifier.get() },
            DeferredRef { objectives.get() },
            DeferredRef { aapsLogger.get() },
            DeferredRef { rxBus.get() },
            DeferredRef { rh.get() },
            DeferredRef { dateUtil.get() },
            DeferredRef { sntpClient.get() },
            DeferredRef { receiverStatusStore.get() },
            DeferredRef { uel.get() },
            DeferredRef { preferences.get() },
            DeferredRef { constraintsChecker.get() },
            DeferredRef { activePlugin.get() },
            DeferredRef { hardLimits.get() },
            DeferredRef { config.get() },
            DeferredRef { persistenceLayer.get() },
            DeferredRef { notificationManager.get() },
            DeferredRef { decimalFormatter.get() },
            DeferredRef { versionCheckerUtils.get() },
            DeferredRef { loop.get() },
            DeferredRef { profileFunction.get() },
            DeferredRef { iobCobCalculator.get() },
            DeferredRef { context.get() }
        )
    }

    /** Merge each of these under the same condition the matching Dagger qualifier had. */
    val allConfigsPlugins: Map<Int, PluginBase> get() = graph.allConfigsPlugins
    val apsPlugins: Map<Int, PluginBase> get() = graph.apsPlugins
    val notNsClientPlugins: Map<Int, PluginBase> get() = graph.notNsClientPlugins
    val viewModelCreators: Map<KClass<*>, MetroViewModelCreator> get() = graph.viewModelCreators
}
