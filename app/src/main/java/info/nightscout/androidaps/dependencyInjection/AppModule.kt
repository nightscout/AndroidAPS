package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefsImpl
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.plugins.general.nsclient.DataSyncSelectorImplementation
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.pump.PumpSyncImplementation
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.androidNotification.NotificationHolderImpl
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.buildHelper.BuildHelperImpl
import info.nightscout.androidaps.utils.buildHelper.ConfigImpl
import info.nightscout.androidaps.utils.resources.IconsProviderImplementation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.rx.DefaultAapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.storage.FileStorage
import info.nightscout.androidaps.utils.storage.Storage
import javax.inject.Singleton

@Suppress("unused")
@Module(includes = [
    AppModule.AppBindings::class
])
open class AppModule {

    @Provides
    fun providesPlugins(config: Config,
                        @PluginsModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
                        @PluginsModule.PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>)
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (config.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (config.APS) plugins += aps.get()
        if (!config.NSCLIENT) plugins += notNsClient.get()
        return plugins.toList().sortedBy { it.first }.map { it.second }
    }

    @Provides
    @Singleton
    fun provideStorage(): Storage = FileStorage()

    @Provides
    @Singleton
    fun provideBuildHelper(config: Config, fileListProvider: PrefFileListProvider): BuildHelper = BuildHelperImpl(config, fileListProvider)

    @Provides
    @Singleton
    internal fun provideSchedulers(): AapsSchedulers = DefaultAapsSchedulers()

    @Provides
    @Singleton
    fun provideProfileFunction(aapsLogger: AAPSLogger, sp: SP, resourceHelper: ResourceHelper, activePlugin: ActivePlugin, repository: AppRepository, dateUtil: DateUtil): ProfileFunction =
        ProfileFunctionImplementation(aapsLogger, sp, resourceHelper, activePlugin, repository, dateUtil)

    @Module
    interface AppBindings {

        @Binds fun bindContext(mainApp: MainApp): Context
        @Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector
        @Binds fun bindActivePluginProvider(pluginStore: PluginStore): ActivePlugin
        @Binds fun bindCommandQueueProvider(commandQueue: CommandQueue): CommandQueueProvider
        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindConfigBuilderInterface(configBuilderPlugin: ConfigBuilderPlugin): ConfigBuilder
        @Binds fun bindNotificationHolderInterface(notificationHolder: NotificationHolderImpl): NotificationHolder
        @Binds fun bindImportExportPrefsInterface(importExportPrefs: ImportExportPrefsImpl): ImportExportPrefs
        @Binds fun bindIconsProviderInterface(iconsProvider: IconsProviderImplementation): IconsProvider
        @Binds fun bindLoopInterface(loopPlugin: LoopPlugin): Loop
        @Binds fun bindIobCobCalculatorInterface(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
        @Binds fun bindSmsCommunicatorInterface(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindDataSyncSelector(dataSyncSelectorImplementation: DataSyncSelectorImplementation): DataSyncSelector

        @Binds fun bindPumpSync(pumpSyncImplementation: PumpSyncImplementation): PumpSync
    }
}

