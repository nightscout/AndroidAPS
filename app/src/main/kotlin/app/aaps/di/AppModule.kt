package app.aaps.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import app.aaps.MainApp
import app.aaps.core.interfaces.configuration.Config
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

@Suppress("unused")
@Module(
    includes = [
        AppModule.AppBindings::class,
        AppModule.Provide::class
    ]
)
open class AppModule {

    @Provides
    fun providesPlugins(
        config: Config,
        @PluginsListModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
        @PluginsListModule.PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        //@PluginsListModule.Unfinished unfinished: Lazy<Map<@JvmSuppressWildcards Int,  @JvmSuppressWildcards PluginBase>>
    )
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (config.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (config.APS) plugins += aps.get()
        if (!config.AAPSCLIENT) plugins += notNsClient.get()
        //if (config.isUnfinishedMode()) plugins += unfinished.get()
        return plugins.toList().sortedBy { it.first }.map { it.second }
    }

    @Module
    open class Provide {

        @Reusable
        @Provides
        fun providesDefaultSharedPreferences(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Module
    interface AppBindings {

        @Binds fun bindContext(mainApp: MainApp): Context
        @Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector
        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindActivityNames(activityNames: UiInteractionImpl): UiInteraction
    }
}

