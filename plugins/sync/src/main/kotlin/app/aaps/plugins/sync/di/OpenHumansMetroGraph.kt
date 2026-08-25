package app.aaps.plugins.sync.di

import android.content.Context
import androidx.work.ListenableWorker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.objects.workflow.WorkerKey
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import app.aaps.plugins.sync.openhumans.OpenHumansWorker
import app.aaps.plugins.sync.openhumans.ui.OHLoginActivity
import app.aaps.plugins.sync.openhumans.ui.OHLoginViewModel
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import kotlin.reflect.KClass

/**
 * Scope marker for the Open Humans uploader.
 *
 * This graph is still a root rather than an extension of `AppRootGraph`, and that is deliberate. A
 * graph extension is generated in the **parent's** module, so its interface must be public - and this
 * one exposes view models whose own types are `internal` to this module. Making them public to satisfy
 * the injector would widen the module's API surface for no other reason, and the exposure cascades:
 * the view model drags its state delegate, which drags its state class.
 *
 * Declaring a scope of its own gets the property that mattered anyway. The danger was never "more than
 * one graph", it was **two graphs declaring the same scope**, which silently gives each its own copy of
 * everything scoped there. `AppScope` now means exactly one graph, so contributed bindings have one
 * unambiguous home.
 */
abstract class OpenHumansScope private constructor()

/**
 * All of Open Humans, on Metro.
 *
 * The point of this graph is that the feature keeps **no** Dagger. The plugin, its three preference
 * delegates, its HTTP client, its upload worker, its activity and its view model are all built here,
 * and `OpenHumansModule` - the Dagger module that used to provide the URLs and the client id - is
 * gone, its five constants now living below.
 *
 * That matters more than converting the plugin alone would. The expensive part of this migration is
 * the bridge between the two frameworks, so the way to make progress is to finish a feature rather
 * than to half-convert several. What still crosses from Dagger is only app-wide infrastructure -
 * logger, resources, preferences, context, database, notifications - which is by definition the last
 * thing that can move.
 *
 * All four Android entry points appear here, which makes this the worked example: a worker
 * ([OpenHumansWorker]), a member-injected activity ([OHLoginActivity], including a qualified
 * `String`), a view model ([OHLoginViewModel]) and a plugin ([OpenHumansUploaderPlugin]).
 */
@DependencyGraph(OpenHumansScope::class)
internal interface OpenHumansMetroGraph : MetroViewModelMultibindings {

    /**
     * Contributed under [NotNSClient], exactly as the Dagger binding was.
     *
     * The qualifier is not decoration: `AppModule.providesPlugins` merges this bucket only when the
     * build is not an AAPSCLIENT one. Dropping it would put Open Humans into follower builds that have
     * never shown it.
     */
    @NotNSClient
    val notNsClientPlugins: Map<Int, PluginBase>

    /** Fills [OHLoginActivity]'s fields - the `@AndroidEntryPoint` replacement. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

    /** Builds [OpenHumansWorker] - the `@HiltWorker` replacement. */
    val workerCreators: Map<KClass<out ListenableWorker>, MetroWorkerCreator>

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides rhRef: DeferredRef<ResourceHelper>,
            @Provides preferencesRef: DeferredRef<Preferences>,
            @Provides contextRef: DeferredRef<Context>,
            @Provides persistenceLayerRef: DeferredRef<PersistenceLayer>,
            @Provides notificationManagerRef: DeferredRef<NotificationManager>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides fabricPrivacyRef: DeferredRef<FabricPrivacy>
        ): OpenHumansMetroGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun rh(r: DeferredRef<ResourceHelper>): ResourceHelper = r.get()
    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun context(r: DeferredRef<Context>): Context = r.get()
    @Provides fun persistenceLayer(r: DeferredRef<PersistenceLayer>): PersistenceLayer = r.get()
    @Provides fun notificationManager(r: DeferredRef<NotificationManager>): NotificationManager = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun fabricPrivacy(r: DeferredRef<FabricPrivacy>): FabricPrivacy = r.get()

    // Moved here from OpenHumansModule, which no longer exists. These are the Open Humans project's
    // own identifiers, so they belong with the rest of the feature.
    @Provides @BaseUrl fun baseUrl(): String = "https://www.openhumans.org"

    @Provides @ClientId fun clientId(): String = "oie6DvnaEOagTxSoD6BukkLPwDhVr6cMlN74Ihz1"

    @Provides @ClientSecret fun clientSecret(): String =
        "jR0N8pkH1jOwtozHc7CsB1UPcJzFN95ldHcK4VGYIApecr8zGJox0v06xLwPLMASScngT12aIaIHXAVCJeKquEXAWG1XekZdbubSpccgNiQBmuVmIF8nc1xSKSNJltCf"

    @Provides @RedirectUrl fun redirectUrl(): String = "androidaps://setup-openhumans"

    @Provides @AuthUrl fun authUrl(@ClientId clientId: String): String =
        "https://www.openhumans.org/direct-sharing/projects/oauth2/authorize/?client_id=$clientId&response_type=code"

    @Provides
    @IntoMap
    @IntKey(340)
    @NotNSClient
    fun bindOpenHumansPlugin(plugin: OpenHumansUploaderPlugin): PluginBase = plugin

    @Provides
    @IntoMap
    @ClassKey(OHLoginActivity::class)
    fun bindOHLoginActivity(injector: MembersInjector<OHLoginActivity>): MembersInjector<*> = injector

    /**
     * The plugin's own screen. Note this class still declares `javax.inject.Inject` - with Dagger
     * interop switched on for this module, Metro reads it, so converting a class means removing the
     * Hilt annotation and adding a binding here, not rewriting its annotations.
     */

    @Provides
    @IntoMap
    @WorkerKey(OpenHumansWorker::class)
    fun bindOpenHumansWorker(factory: OpenHumansWorker.Factory): MetroWorkerCreator = factory
}
