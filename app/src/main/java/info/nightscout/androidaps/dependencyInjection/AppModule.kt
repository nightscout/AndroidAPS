package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.storage.FileStorage
import info.nightscout.androidaps.utils.storage.Storage
import javax.inject.Singleton

@Module(includes = [
    AppModule.AppBindings::class,
    PluginsModule::class,
    SkinsModule::class
])
open class AppModule {

    @Provides
    @Singleton
    fun provideProfileFunction(injector: HasAndroidInjector, aapsLogger: AAPSLogger, sp: SP, resourceHelper: ResourceHelper, activePlugin: ActivePluginProvider, fabricPrivacy: FabricPrivacy): ProfileFunction {
        return ProfileFunctionImplementation(injector, aapsLogger, sp, resourceHelper, activePlugin, fabricPrivacy)
    }

    @Provides
    fun providesPlugins(configInterface: ConfigInterface,
                        @PluginsModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
                        @PluginsModule.PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.NSClient nsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>)
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (configInterface.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (configInterface.APS) plugins += aps.get()
        if (!configInterface.NSCLIENT) plugins += notNsClient.get()
        if (configInterface.NSCLIENT) plugins += nsClient.get()
        return plugins.toList().sortedBy { it.first }.map { it.second }
    }

    @Provides
    @Singleton
    fun provideStorage(): Storage {
        return FileStorage()
    }

    @Module
    interface AppBindings {

        @Binds fun bindContext(mainApp: MainApp): Context
        @Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector
        @Binds fun bindDatabaseHelperInterface(mainApp: MainApp): DatabaseHelperInterface
        @Binds fun bindActivePluginProvider(pluginStore: PluginStore): ActivePluginProvider
        @Binds fun commandQueueProvider(commandQueue: CommandQueue): CommandQueueProvider
        @Binds fun configInterfaceProvider(config: Config): ConfigInterface
        @Binds fun treatmentInterfaceProvider(treatmentsPlugin: TreatmentsPlugin): TreatmentsInterface

    }
}
