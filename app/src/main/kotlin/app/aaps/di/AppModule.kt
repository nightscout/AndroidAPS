package app.aaps.di

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.configuration.Config
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            metroGraphs: MetroGraphs,
            aapsLogger: AAPSLogger
        ): List<@JvmSuppressWildcards PluginBase> {
            // Every plugin is contributed by Metro now. The four Dagger buckets that used to be merged
            // here are gone: nothing had contributed to them since the eros module left the build, so
            // they added four always-empty maps and four ways for the merge to go wrong unnoticed.
            //
            // Sources are still listed rather than merged directly, so mergePlugins can name which
            // bucket a clashing plugin came from.
            val sources = buildList {
                add(PluginSource("Metro", metroGraphs.plugins()))
                // Each qualified bucket is merged only under the condition that build should have it.
                // Keeping them apart is what stops a plugin appearing in a build that never had it -
                // a follower showing Objectives, say.
                if (config.APS) add(PluginSource("Metro @APS", metroGraphs.apsPlugins()))
                if (config.PUMPDRIVERS) add(PluginSource("Metro @PumpDriver", metroGraphs.pumpDriverPlugins()))
                if (!config.AAPSCLIENT) add(PluginSource("Metro @NotNSClient", metroGraphs.notNsClientPlugins()))
            }

            val (plugins, problems) = mergePlugins(sources)
            // Two buckets can still collide on one order key, which loses a plugin silently. Logged
            // rather than thrown: a wrong plugin list must not stop the app from starting, and this is
            // loud enough to find in a log.
            problems.forEach { aapsLogger.error(LTag.CORE, "PLUGIN LIST: $it") }

            return plugins
        }


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


        // Scope on the implementation, not on the binding: Metro reads this module now that interop is
        // on for `:app`, and it rejects a scoped @Binds. `HistoryBrowserData` carries @Singleton itself,
        // so the instance is still single.
        @Binds fun bindHistoryScope(impl: HistoryBrowserData): HistoryScope
    }
}

