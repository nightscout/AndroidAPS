package app.aaps.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.implementations.ConfigImpl
import app.aaps.implementations.UiInteractionImpl
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.android.HasAndroidInjector
import dagger.hilt.migration.DisableInstallInCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Injections needed for [TestApplication].
 * Uses plain Dagger component (DaggerTestAppComponent), not Hilt.
 */
@Suppress("unused")
@Module(
    includes = [
        TestModule.AppBindings::class,
        TestModule.Provide::class
    ]
)
@DisableInstallInCheck
open class TestModule {

    @Provides
    fun providesPlugins(
        config: Config,
        @PluginsListModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
        @PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        //@PluginsListModule.Unfinished unfinished: Lazy<Map<@JvmSuppressWildcards Int,  @JvmSuppressWildcards PluginBase>>
    )
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (config.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (config.APS) plugins += aps.get()
        if (!config.AAPSCLIENT) plugins += notNsClient.get()
        //if (config.isEnabled(ExternalOptions.UNFINISHED_MODE)) plugins += unfinished.get()
        return plugins.toList().sortedBy { it.first }.map { it.second }
    }

    @Module
    @DisableInstallInCheck
    open class Provide {

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Reusable
        @Provides
        fun providesDefaultSharedPreferences(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Module
    @DisableInstallInCheck
    interface AppBindings {

        @Binds fun bindContext(mainApp: TestApplication): Context
        @Binds fun bindInjector(mainApp: TestApplication): HasAndroidInjector
        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindActivityNames(activityNames: UiInteractionImpl): UiInteraction
    }
}

