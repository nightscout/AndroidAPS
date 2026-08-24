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
import app.aaps.plugins.source.instara.InstaraStaleCheckWorker
import app.aaps.plugins.source.notificationreader.NotificationCollectorService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * Metro wiring for the blood-glucose source workers and the notification reader service.
 *
 * Twelve workers is the first real volume test of the worker seam - the ones before this were single
 * cases proving it worked at all. Each is one `@Provides @IntoMap @ClassKey` line here plus a nested
 * `@AssistedFactory` on the worker, and nothing else about the workers changed: they keep their
 * `javax.inject` dependencies, which Dagger interop lets Metro read.
 *
 * One thing this batch taught: Metro matches assisted parameters **by name**, and
 * `InstaraStaleCheckWorker` called its context `ctx`. That is a compile error rather than a silent
 * mismatch, but it is a rename every worker conversion has to check.
 *
 * [NotificationCollectorService] is a member-injection case rather than a worker, and it cannot use
 * `MetroService`, because it already extends `NotificationListenerService`. It calls the injector
 * itself, which is all `MetroService` does.
 */
@DependencyGraph(AppScope::class)
internal interface SourceMetroGraph {

    /** Workers this module owns, keyed by class. `MetroWorkerFactory` looks them up by class name. */
    val workerCreators: Map<KClass<*>, MetroWorkerCreator>

    /** Android classes that fill their own fields - here, only the notification reader service. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides fabricPrivacyRef: DeferredRef<FabricPrivacy>,
            @Provides persistenceLayerRef: DeferredRef<PersistenceLayer>,
            @Provides preferencesRef: DeferredRef<Preferences>,
            @Provides dateUtilRef: DeferredRef<DateUtil>,
            @Provides profileUtilRef: DeferredRef<ProfileUtil>,
            @Provides profileFunctionRef: DeferredRef<ProfileFunction>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides contextRef: DeferredRef<Context>,
            @Provides dataInboxRef: DeferredRef<DataInbox>,
            @Provides notificationManagerRef: DeferredRef<NotificationManager>,
            @Provides virtualPumpRef: DeferredRef<VirtualPump>,
            @Provides aidexRef: DeferredRef<AidexPlugin>,
            @Provides dexcomRef: DeferredRef<DexcomPlugin>,
            @Provides glimpRef: DeferredRef<GlimpPlugin>,
            @Provides instaraRef: DeferredRef<InstaraPlugin>,
            @Provides mm640gRef: DeferredRef<MM640gPlugin>,
            @Provides patchedSiAppRef: DeferredRef<PatchedSiAppPlugin>,
            @Provides patchedSinoAppRef: DeferredRef<PatchedSinoAppPlugin>,
            @Provides poctechRef: DeferredRef<PoctechPlugin>,
            @Provides syaiRef: DeferredRef<SyaiPlugin>,
            @Provides tomatoRef: DeferredRef<TomatoPlugin>,
            @Provides xdripRef: DeferredRef<XdripSourcePlugin>,
            @Provides notificationReaderRef: DeferredRef<NotificationReaderPlugin>
        ): SourceMetroGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun fabricPrivacy(r: DeferredRef<FabricPrivacy>): FabricPrivacy = r.get()
    @Provides fun persistenceLayer(r: DeferredRef<PersistenceLayer>): PersistenceLayer = r.get()
    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun dateUtil(r: DeferredRef<DateUtil>): DateUtil = r.get()
    @Provides fun profileUtil(r: DeferredRef<ProfileUtil>): ProfileUtil = r.get()
    @Provides fun profileFunction(r: DeferredRef<ProfileFunction>): ProfileFunction = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun context(r: DeferredRef<Context>): Context = r.get()
    @Provides fun dataInbox(r: DeferredRef<DataInbox>): DataInbox = r.get()
    @Provides fun notificationManager(r: DeferredRef<NotificationManager>): NotificationManager = r.get()
    @Provides fun virtualPump(r: DeferredRef<VirtualPump>): VirtualPump = r.get()
    @Provides fun aidex(r: DeferredRef<AidexPlugin>): AidexPlugin = r.get()
    @Provides fun dexcom(r: DeferredRef<DexcomPlugin>): DexcomPlugin = r.get()
    @Provides fun glimp(r: DeferredRef<GlimpPlugin>): GlimpPlugin = r.get()
    @Provides fun instara(r: DeferredRef<InstaraPlugin>): InstaraPlugin = r.get()
    @Provides fun mm640g(r: DeferredRef<MM640gPlugin>): MM640gPlugin = r.get()
    @Provides fun patchedSiApp(r: DeferredRef<PatchedSiAppPlugin>): PatchedSiAppPlugin = r.get()
    @Provides fun patchedSinoApp(r: DeferredRef<PatchedSinoAppPlugin>): PatchedSinoAppPlugin = r.get()
    @Provides fun poctech(r: DeferredRef<PoctechPlugin>): PoctechPlugin = r.get()
    @Provides fun syai(r: DeferredRef<SyaiPlugin>): SyaiPlugin = r.get()
    @Provides fun tomato(r: DeferredRef<TomatoPlugin>): TomatoPlugin = r.get()
    @Provides fun xdrip(r: DeferredRef<XdripSourcePlugin>): XdripSourcePlugin = r.get()
    @Provides fun notificationReader(r: DeferredRef<NotificationReaderPlugin>): NotificationReaderPlugin = r.get()

    @Provides @IntoMap @ClassKey(AidexPlugin.AidexWorker::class)
    fun bindAidexWorker(f: AidexPlugin.AidexWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(DexcomPlugin.DexcomWorker::class)
    fun bindDexcomWorker(f: DexcomPlugin.DexcomWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(GlimpPlugin.GlimpWorker::class)
    fun bindGlimpWorker(f: GlimpPlugin.GlimpWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(InstaraPlugin.InstaraWorker::class)
    fun bindInstaraWorker(f: InstaraPlugin.InstaraWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(InstaraStaleCheckWorker::class)
    fun bindInstaraStaleCheckWorker(f: InstaraStaleCheckWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(MM640gPlugin.MM640gWorker::class)
    fun bindMM640gWorker(f: MM640gPlugin.MM640gWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(PatchedSiAppPlugin.PatchedSiAppWorker::class)
    fun bindPatchedSiAppWorker(f: PatchedSiAppPlugin.PatchedSiAppWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(PatchedSinoAppPlugin.PatchedSinoAppWorker::class)
    fun bindPatchedSinoAppWorker(f: PatchedSinoAppPlugin.PatchedSinoAppWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(PoctechPlugin.PoctechWorker::class)
    fun bindPoctechWorker(f: PoctechPlugin.PoctechWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(SyaiPlugin.SyaiWorker::class)
    fun bindSyaiWorker(f: SyaiPlugin.SyaiWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(TomatoPlugin.TomatoWorker::class)
    fun bindTomatoWorker(f: TomatoPlugin.TomatoWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(XdripSourcePlugin.XdripSourceWorker::class)
    fun bindXdripSourceWorker(f: XdripSourcePlugin.XdripSourceWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(NotificationCollectorService::class)
    fun bindNotificationCollectorService(
        injector: MembersInjector<NotificationCollectorService>
    ): MembersInjector<*> = injector
}
