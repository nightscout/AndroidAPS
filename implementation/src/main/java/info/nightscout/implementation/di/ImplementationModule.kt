package info.nightscout.implementation.di

import android.content.Context
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.plugins.general.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.utils.CryptoUtil
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.versionChecker.VersionCheckerUtils
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.AndroidPermissionImpl
import info.nightscout.implementation.BolusTimerImpl
import info.nightscout.implementation.CarbTimerImpl
import info.nightscout.implementation.DefaultValueHelperImpl
import info.nightscout.implementation.HardLimitsImpl
import info.nightscout.implementation.LocalAlertUtilsImpl
import info.nightscout.implementation.TranslatorImpl
import info.nightscout.implementation.TrendCalculatorImpl
import info.nightscout.implementation.UserEntryLoggerImpl
import info.nightscout.implementation.XDripBroadcastImpl
import info.nightscout.implementation.androidNotification.NotificationHolderImpl
import info.nightscout.implementation.logging.LoggerUtilsImpl
import info.nightscout.implementation.maintenance.PrefFileListProviderImpl
import info.nightscout.implementation.overview.OverviewDataImpl
import info.nightscout.implementation.profiling.ProfilerImpl
import info.nightscout.implementation.protection.PasswordCheckImpl
import info.nightscout.implementation.protection.ProtectionCheckImpl
import info.nightscout.implementation.pump.PumpSyncImplementation
import info.nightscout.implementation.pump.WarnColorsImpl
import info.nightscout.implementation.queue.CommandQueueImplementation
import info.nightscout.implementation.resources.IconsProviderImplementation
import info.nightscout.implementation.resources.ResourceHelperImpl
import info.nightscout.implementation.stats.DexcomTirCalculatorImpl
import info.nightscout.implementation.stats.TddCalculatorImpl
import info.nightscout.implementation.stats.TirCalculatorImpl
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.BolusTimer
import info.nightscout.interfaces.CarbTimer
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.LocalAlertUtils
import info.nightscout.interfaces.NotificationHolder
import info.nightscout.interfaces.Translator
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.logging.LoggerUtils
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.protection.PasswordCheck
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.stats.DexcomTirCalculator
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.stats.TirCalculator
import info.nightscout.interfaces.storage.Storage
import info.nightscout.interfaces.ui.IconsProvider
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.TrendCalculator
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Singleton

@Module(
    includes = [
        CommandQueueModule::class
    ]
)

@Suppress("unused")
open class ImplementationModule {

    @Provides
    @Singleton
    fun provideResources(context: Context, fabricPrivacy: FabricPrivacy): ResourceHelper =
        ResourceHelperImpl(context, fabricPrivacy)

    @Provides
    @Singleton
    fun provideHardLimits(aapsLogger: AAPSLogger, rxBus: RxBus, sp: SP, rh: ResourceHelper, context: Context, repository: AppRepository): HardLimits =
        HardLimitsImpl(aapsLogger, rxBus, sp, rh, context, repository)

    @Provides
    @Singleton
    fun provideWarnColors(rh: ResourceHelper): WarnColors = WarnColorsImpl(rh)

    @Provides
    @Singleton
    fun provideProfiler(aapsLogger: AAPSLogger): Profiler = ProfilerImpl(aapsLogger)

    @Provides
    @Singleton
    fun provideLoggerUtils(prefFileListProvider: PrefFileListProvider): LoggerUtils = LoggerUtilsImpl(prefFileListProvider)

    @Provides
    @Singleton
    fun providePasswordCheck(sp: SP, cryptoUtil: CryptoUtil): PasswordCheck = PasswordCheckImpl(sp, cryptoUtil)

    @Provides
    @Singleton
    fun provideProtectionCheck(sp: SP, passwordCheck: PasswordCheck, dateUtil: DateUtil): ProtectionCheck = ProtectionCheckImpl(sp, passwordCheck, dateUtil)

    @Provides
    @Singleton
    fun provideDefaultValueHelper(sp: SP, profileFunction: ProfileFunction): DefaultValueHelper = DefaultValueHelperImpl(sp, profileFunction)

    @Provides
    @Singleton
    fun provideTranslator(rh: ResourceHelper): Translator = TranslatorImpl(rh)

    @Provides
    @Singleton
    fun provideUserEntryLogger(
        aapsLogger: AAPSLogger,
        repository: AppRepository,
        aapsSchedulers: AapsSchedulers,
        dateUtil: DateUtil
    ): UserEntryLogger = UserEntryLoggerImpl(aapsLogger, repository, aapsSchedulers, dateUtil)

    @Provides
    @Singleton
    fun provideOverviewData(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        dateUtil: DateUtil,
        sp: SP,
        activePlugin: ActivePlugin,
        defaultValueHelper: DefaultValueHelper,
        profileFunction: ProfileFunction,
        repository: AppRepository
    ): OverviewData = OverviewDataImpl(aapsLogger, rh, dateUtil, sp, activePlugin, defaultValueHelper, profileFunction, repository)

    @Provides
    @Singleton
    fun providePrefFileListProvider(
        rh: ResourceHelper,
        config: Lazy<Config>,
        encryptedPrefsFormat: EncryptedPrefsFormat,
        storage: Storage,
        versionCheckerUtils: VersionCheckerUtils,
        context: Context
    ): PrefFileListProvider = PrefFileListProviderImpl(rh, config, encryptedPrefsFormat, storage, versionCheckerUtils, context)

    @Module
    interface Bindings {
        @Binds fun bindTrendCalculatorInterface(trendCalculator: TrendCalculatorImpl): TrendCalculator
        @Binds fun bindTddCalculatorInterface(tddCalculator: TddCalculatorImpl): TddCalculator
        @Binds fun bindTirCalculatorInterface(tirCalculator: TirCalculatorImpl): TirCalculator
        @Binds fun bindDexcomTirCalculatorInterface(dexcomTirCalculator: DexcomTirCalculatorImpl): DexcomTirCalculator
        @Binds fun bindPumpSyncInterface(pumpSyncImplementation: PumpSyncImplementation): PumpSync
        @Binds fun bindXDripBroadcastInterface(xDripBroadcastImpl: XDripBroadcastImpl): XDripBroadcast
        @Binds fun bindCarbTimerInterface(carbTimer: CarbTimerImpl): CarbTimer
        @Binds fun bindBolusTimerInterface(bolusTimer: BolusTimerImpl): BolusTimer
        @Binds fun bindAndroidPermissionInterface(androidPermission: AndroidPermissionImpl): AndroidPermission
        @Binds fun bindLocalAlertUtilsInterface(localAlertUtils: LocalAlertUtilsImpl): LocalAlertUtils
        @Binds fun bindIconsProviderInterface(iconsProvider: IconsProviderImplementation): IconsProvider
        @Binds fun bindNotificationHolderInterface(notificationHolder: NotificationHolderImpl): NotificationHolder
        @Binds fun bindCommandQueue(commandQueue: CommandQueueImplementation): CommandQueue
    }
}