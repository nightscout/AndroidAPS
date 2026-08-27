package app.aaps.di

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.di.metro.MetroGraphs
import app.aaps.history.HistoryBrowserData
import app.aaps.implementations.ConfigImpl
import app.aaps.implementations.UiInteractionImpl
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.ui.compose.history.HistoryScope
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Suppress("unused")
@Module(
    includes = [
        AppModule.AppBindings::class,
        AppModule.Provide::class
    ]
)
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Module
    @InstallIn(SingletonComponent::class)
    object Provide {

        @Provides
        fun providesPlugins(
            config: Config,
            @AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
            @PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
            @NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
            @APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
            metroGraphs: MetroGraphs,
            aapsLogger: AAPSLogger,
            //@PluginsListModule.Unfinished unfinished: Lazy<Map<@JvmSuppressWildcards Int,  @JvmSuppressWildcards PluginBase>>
        )
            : List<@JvmSuppressWildcards PluginBase> {
            // Sources are listed rather than merged directly, so mergePlugins can see which one a
            // plugin came from and report a clash by name. Modules migrated to Metro contribute a
            // compile-time @IntoMap multibinding of the same shape, on the same Int order, so modules
            // can move one at a time.
            val sources = buildList {
                add(PluginSource("Dagger @AllConfigs", allConfigs))
                if (config.PUMPDRIVERS) add(PluginSource("Dagger @PumpDriver", pumpDrivers.get()))
                if (config.APS) add(PluginSource("Dagger @APS", aps.get()))
                if (!config.AAPSCLIENT) add(PluginSource("Dagger @NotNSClient", notNsClient.get()))
                //if (config.isEnabled(ExternalOptions.UNFINISHED_MODE)) add(PluginSource("Dagger unfinished", unfinished.get()))
                add(PluginSource("Metro", metroGraphs.plugins()))
                // Metro's qualified buckets, each merged under the same condition as the matching
                // Dagger one above. Keeping them apart is what stops a converted plugin appearing in
                // a build that never had it - a follower showing Objectives, say.
                if (config.APS) add(PluginSource("Metro @APS", metroGraphs.apsPlugins()))
                if (config.PUMPDRIVERS) add(PluginSource("Metro @PumpDriver", metroGraphs.pumpDriverPlugins()))
                if (!config.AAPSCLIENT) add(PluginSource("Metro @NotNSClient", metroGraphs.notNsClientPlugins()))
            }

            val (plugins, problems) = mergePlugins(sources)
            // While Dagger and Metro both contribute, a plugin can be lost or doubled without either
            // framework noticing. Logged rather than thrown: a wrong plugin list must not stop the app
            // from starting, and this is loud enough to find in a log.
            problems.forEach { aapsLogger.error(LTag.CORE, "PLUGIN LIST: $it") }

            return plugins
        }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)


        /**
         * Built by Metro, in the multiplatform module that owns the worker it schedules. Dagger
         * delegates rather than constructs: building it on both sides would give two schedulers,
         * each posting its own expiry work.
         */
        @Provides
        @Singleton
        fun provideRunningModeExpiryScheduler(metroGraphs: MetroGraphs): RunningModeExpiryScheduler =
            metroGraphs.runningModeExpiryScheduler

        @Provides
        fun provideContext(@ApplicationContext context: Context): Context = context
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface AppBindings {

        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindActivityNames(activityNames: UiInteractionImpl): UiInteraction

        // Scope on the implementation, not on the binding: Metro reads this module now that interop is
        // on for `:app`, and it rejects a scoped @Binds. `HistoryBrowserData` carries @Singleton itself,
        // so the instance is still single.
        @Binds fun bindHistoryScope(impl: HistoryBrowserData): HistoryScope
    }
}

