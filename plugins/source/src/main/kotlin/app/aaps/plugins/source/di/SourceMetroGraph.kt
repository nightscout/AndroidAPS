package app.aaps.plugins.source.di

import android.content.Context
import androidx.work.ListenableWorker
import app.aaps.core.interfaces.db.PersistenceLayer
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
import app.aaps.core.objects.workflow.WorkerKey
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
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * Scope marker for this module's workers and member injectors.
 *
 * The graph used to be a second root on `AppScope`, which was unsafe: two graphs declaring one scope
 * each get their own copy of anything scoped there, silently. Now it is an extension with a scope of
 * its own, so `AppScope` means exactly one graph.
 */
abstract class SourceScope private constructor()

/**
 * Metro wiring for the blood-glucose source workers and the notification reader service.
 *
 * Twelve workers is the first real volume test of the worker seam - the ones before this were single
 * cases proving it worked at all. Each is one `@Provides @IntoMap @WorkerKey` line here plus a nested
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
@GraphExtension(SourceScope::class)
interface SourceMetroGraph {

    /** Workers this module owns, keyed by class. `MetroWorkerFactory` looks them up by class name. */
    val workerCreators: Map<KClass<out ListenableWorker>, MetroWorkerCreator>

    /** Android classes that fill their own fields - here, only the notification reader service. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

    @Provides @IntoMap @WorkerKey(AidexPlugin.AidexWorker::class)
    fun bindAidexWorker(f: AidexPlugin.AidexWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(DexcomPlugin.DexcomWorker::class)
    fun bindDexcomWorker(f: DexcomPlugin.DexcomWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(GlimpPlugin.GlimpWorker::class)
    fun bindGlimpWorker(f: GlimpPlugin.GlimpWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(InstaraPlugin.InstaraWorker::class)
    fun bindInstaraWorker(f: InstaraPlugin.InstaraWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(InstaraStaleCheckWorker::class)
    fun bindInstaraStaleCheckWorker(f: InstaraStaleCheckWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(MM640gPlugin.MM640gWorker::class)
    fun bindMM640gWorker(f: MM640gPlugin.MM640gWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(PatchedSiAppPlugin.PatchedSiAppWorker::class)
    fun bindPatchedSiAppWorker(f: PatchedSiAppPlugin.PatchedSiAppWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(PatchedSinoAppPlugin.PatchedSinoAppWorker::class)
    fun bindPatchedSinoAppWorker(f: PatchedSinoAppPlugin.PatchedSinoAppWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(PoctechPlugin.PoctechWorker::class)
    fun bindPoctechWorker(f: PoctechPlugin.PoctechWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(SyaiPlugin.SyaiWorker::class)
    fun bindSyaiWorker(f: SyaiPlugin.SyaiWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(TomatoPlugin.TomatoWorker::class)
    fun bindTomatoWorker(f: TomatoPlugin.TomatoWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @WorkerKey(XdripSourcePlugin.XdripSourceWorker::class)
    fun bindXdripSourceWorker(f: XdripSourcePlugin.XdripSourceWorker.Factory): MetroWorkerCreator = f

    @Provides @IntoMap @ClassKey(NotificationCollectorService::class)
    fun bindNotificationCollectorService(
        injector: MembersInjector<NotificationCollectorService>
    ): MembersInjector<*> = injector
}
