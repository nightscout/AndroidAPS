package info.nightscout.androidaps.di

import android.content.Context
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.AndroidPermission
import info.nightscout.androidaps.interfaces.Autotune
import info.nightscout.androidaps.interfaces.BolusTimer
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.interfaces.CarbTimer
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.IconsProvider
import info.nightscout.androidaps.interfaces.ImportExportPrefs
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.LocalAlertUtils
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.NotificationHolder
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.interfaces.SmsCommunicator
import info.nightscout.androidaps.interfaces.XDripBroadcast
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.general.autotune.AutotunePlugin
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefsImpl
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.plugins.general.nsclient.DataSyncSelectorImplementation
import info.nightscout.androidaps.plugins.general.nsclient.data.DeviceStatusData
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.pump.PumpSyncImplementation
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.androidNotification.NotificationHolderImpl
import info.nightscout.androidaps.utils.buildHelper.BuildHelperImpl
import info.nightscout.androidaps.utils.buildHelper.ConfigImpl
import info.nightscout.androidaps.utils.resources.IconsProviderImplementation
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.rx.DefaultAapsSchedulers
import info.nightscout.androidaps.utils.storage.FileStorage
import info.nightscout.androidaps.utils.storage.Storage
import info.nightscout.implementation.AndroidPermissionImpl
import info.nightscout.implementation.BolusTimerImpl
import info.nightscout.implementation.CarbTimerImpl
import info.nightscout.implementation.LocalAlertUtilsImpl
import info.nightscout.implementation.XDripBroadcastImpl
import info.nightscout.implementation.queue.CommandQueueImplementation
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Singleton

@Suppress("unused")
@Module(includes = [
    AppModule.AppBindings::class
])
open class AppModule {

    @Provides
    fun providesPlugins(config: Config, buildHelper: BuildHelper,
                        @PluginsModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
                        @PluginsModule.PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.Unfinished unfinished: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>)
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (config.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (config.APS) plugins += aps.get()
        if (!config.NSCLIENT) plugins += notNsClient.get()
        if (buildHelper.isUnfinishedMode()) plugins += unfinished.get()
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
    fun provideProfileFunction(
        aapsLogger: AAPSLogger, sp: SP, rxBus: RxBus, rh:
        ResourceHelper, activePlugin:
        ActivePlugin, repository: AppRepository, dateUtil: DateUtil, config: Config, hardLimits: HardLimits,
        aapsSchedulers: AapsSchedulers, fabricPrivacy: FabricPrivacy, deviceStatusData: DeviceStatusData
    ): ProfileFunction =
        ProfileFunctionImplementation(
            aapsLogger, sp, rxBus, rh, activePlugin, repository, dateUtil,
            config, hardLimits, aapsSchedulers, fabricPrivacy, deviceStatusData
        )

    @Module
    interface AppBindings {

        @Binds fun bindContext(mainApp: MainApp): Context
        @Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector
        @Binds fun bindActivePlugin(pluginStore: PluginStore): ActivePlugin
        @Binds fun bindCommandQueue(commandQueue: CommandQueueImplementation): CommandQueue
        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindConfigBuilderInterface(configBuilderPlugin: ConfigBuilderPlugin): ConfigBuilder
        @Binds fun bindNotificationHolderInterface(notificationHolder: NotificationHolderImpl): NotificationHolder
        @Binds fun bindImportExportPrefsInterface(importExportPrefs: ImportExportPrefsImpl): ImportExportPrefs
        @Binds fun bindIconsProviderInterface(iconsProvider: IconsProviderImplementation): IconsProvider
        @Binds fun bindLoopInterface(loopPlugin: LoopPlugin): Loop
        @Binds fun bindAutotuneInterface(autotunePlugin: AutotunePlugin): Autotune
        @Binds fun bindIobCobCalculatorInterface(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
        @Binds fun bindSmsCommunicatorInterface(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindDataSyncSelector(dataSyncSelectorImplementation: DataSyncSelectorImplementation): DataSyncSelector

        @Binds fun bindPumpSyncInterface(pumpSyncImplementation: PumpSyncImplementation): PumpSync
        @Binds fun bindXDripBroadcastInterface(xDripBroadcastImpl: XDripBroadcastImpl): XDripBroadcast
        @Binds fun bindCarbTimerInterface(carbTimer: CarbTimerImpl): CarbTimer
        @Binds fun bindBolusTimerInterface(bolusTimer: BolusTimerImpl): BolusTimer
        @Binds fun bindAndroidPermissionInterface(androidPermission: AndroidPermissionImpl): AndroidPermission
        @Binds fun bindLocalAlertUtilsInterface(localAlertUtils: LocalAlertUtilsImpl): LocalAlertUtils
    }
}

