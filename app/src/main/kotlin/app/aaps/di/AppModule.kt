package app.aaps.di

import android.content.Context
import android.content.SharedPreferences
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
import dagger.android.AndroidInjectionModule
import dagger.android.HasAndroidInjector
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
        AndroidInjectionModule::class,
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

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Reusable
        @Provides
        fun providesDefaultSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

        @Provides
        fun provideContext(@ApplicationContext context: Context): Context = context

        @Provides
        fun provideHasAndroidInjector(@ApplicationContext context: Context): HasAndroidInjector =
            context.applicationContext as HasAndroidInjector
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface AppBindings {

        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindActivityNames(activityNames: UiInteractionImpl): UiInteraction
    }
}

