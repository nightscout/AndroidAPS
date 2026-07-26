package app.aaps.implementation.di

import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.alerts.LocalAlertUtilsImpl
import app.aaps.implementation.androidNotification.AlarmSoundPlayerImpl
import app.aaps.implementation.androidNotification.NotificationHolderImpl
import app.aaps.implementation.aps.DetermineBasalResult
import app.aaps.implementation.bolus.BatchExecutorImpl
import app.aaps.implementation.db.ProcessedTbrEbDataImpl
import app.aaps.implementation.insulin.ConcentrationHelperImpl
import app.aaps.implementation.insulin.InsulinImpl
import app.aaps.implementation.iob.AutosensDataObject
import app.aaps.implementation.iob.GlucoseStatusProviderImpl
import app.aaps.implementation.locale.LocaleDependentSettingImpl
import app.aaps.implementation.logging.LoggerUtilsImpl
import app.aaps.implementation.logging.UserEntryLoggerImpl
import app.aaps.implementation.notifications.NotificationManagerImpl
import app.aaps.implementation.overview.LastBgDataImpl
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.preference.VisibilityContextImpl
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.implementation.profile.ProfileRepositoryImpl
import app.aaps.implementation.profile.ProfileStoreObject
import app.aaps.implementation.profile.ProfileUtilImpl
import app.aaps.implementation.profiling.ProfilerImpl
import app.aaps.implementation.protection.ExportPasswordDataStoreImpl
import app.aaps.implementation.protection.PasswordCheckImpl
import app.aaps.implementation.protection.ProtectionCheckImpl
import app.aaps.implementation.protection.SecureEncryptImpl
import app.aaps.implementation.pump.BlePreCheckImpl
import app.aaps.implementation.pump.DetailedBolusInfoStorageImpl
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.implementation.pump.PumpStatusProviderImpl
import app.aaps.implementation.pump.PumpSyncImplementation
import app.aaps.implementation.pump.PumpWithConcentrationImpl
import app.aaps.implementation.pump.TemporaryBasalStorageImpl
import app.aaps.implementation.receivers.BTReceiver
import app.aaps.implementation.receivers.ChargingStateReceiver
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.implementation.receivers.ReceiverStatusStoreImpl
import app.aaps.implementation.receivers.TimeDateOrTZChangeReceiver
import app.aaps.implementation.resources.IconsProviderImplementation
import app.aaps.implementation.resources.ResourceHelperImpl
import app.aaps.implementation.sharedPreferences.PreferencesImpl
import app.aaps.implementation.stats.DexcomTirCalculatorImpl
import app.aaps.implementation.stats.TddCalculatorImpl
import app.aaps.implementation.stats.TirCalculatorImpl
import app.aaps.implementation.storage.FileStorage
import app.aaps.implementation.userEntry.UserEntryPresentationHelperImpl
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.implementation.utils.HardLimitsImpl
import app.aaps.implementation.utils.TranslatorImpl
import app.aaps.implementation.utils.TrendCalculatorImpl
import app.aaps.implementation.utils.fabric.FabricPrivacyImpl
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module(
    includes = [
        ImplementationModule.Bindings::class,
        CommandQueueModule::class,
        MaintenanceImplModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
class ImplementationModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @ContributesAndroidInjector fun contributesNetworkChangeReceiver(): NetworkChangeReceiver
        @ContributesAndroidInjector fun contributesBTReceiver(): BTReceiver
        @ContributesAndroidInjector fun contributesChargingStateReceiver(): ChargingStateReceiver
        @ContributesAndroidInjector fun contributesTimeDateOrTZChangeReceiver(): TimeDateOrTZChangeReceiver

        @Binds fun bindPreferences(preferencesImpl: PreferencesImpl): Preferences
        @Binds fun bindVisibilityContext(impl: VisibilityContextImpl): VisibilityContext
        @Binds fun bindFabricPrivacy(fabricPrivacyImpl: FabricPrivacyImpl): FabricPrivacy
        @Binds fun bindActivePlugin(pluginStore: PluginStore): ActivePlugin

        // Runtime-permission sources for non-plugin features (e.g. standalone Automation).
        // May be empty; contributors bind via @IntoSet PermissionProvider.
        @Multibinds fun permissionProviders(): Set<PermissionProvider>

        @Binds fun bindLastBgData(lastBgData: LastBgDataImpl): LastBgData
        @Binds fun bindOverviewData(overviewData: OverviewDataImpl): OverviewData
        @Binds fun bindProcessedTbrEbData(pProcessedTbrEbData: ProcessedTbrEbDataImpl): ProcessedTbrEbData
        @Binds fun bindUserEntryLogger(userEntryLoggerImpl: UserEntryLoggerImpl): UserEntryLogger
        @Binds fun bindInsulin(insulinImpl: InsulinImpl): Insulin
        @Binds fun bindInsulinManager(insulinImpl: InsulinImpl): InsulinManager
        @Binds fun bindBatchExecutor(impl: BatchExecutorImpl): BatchExecutor
        @Binds fun bindConcentrationHelper(concentrationHelperImpl: ConcentrationHelperImpl): ConcentrationHelper
        @Binds fun bindDetailedBolusInfoStorage(detailedBolusInfoStorageImpl: DetailedBolusInfoStorageImpl): DetailedBolusInfoStorage
        @Binds fun bindTemporaryBasalStorage(temporaryBasalStorageImpl: TemporaryBasalStorageImpl): TemporaryBasalStorage
        @Binds fun bindTranslator(translatorImpl: TranslatorImpl): Translator
        @Binds fun bindProtectionCheck(protectionCheckImpl: ProtectionCheckImpl): ProtectionCheck
        @Binds fun bindPasswordCheck(passwordCheckImpl: PasswordCheckImpl): PasswordCheck
        @Binds fun bindExportPasswordCheck(exportPasswordDataStoreImpl: ExportPasswordDataStoreImpl): ExportPasswordDataStore
        @Binds fun bindSecureEncrypt(secureEncryptImpl: SecureEncryptImpl): SecureEncrypt
        @Binds fun bindLoggerUtils(loggerUtilsImpl: LoggerUtilsImpl): LoggerUtils
        @Binds fun bindProfiler(profilerImpl: ProfilerImpl): Profiler
        @Binds fun bindHardLimits(hardLimitsImpl: HardLimitsImpl): HardLimits
        @Binds fun bindResourceHelper(resourceHelperImpl: ResourceHelperImpl): ResourceHelper
        @Binds fun bindBlePreCheck(blePreCheckImpl: BlePreCheckImpl): BlePreCheck
        @Binds fun bindLocaleDependentSetting(localeDependentSettingImpl: LocaleDependentSettingImpl): LocaleDependentSetting
        @Binds fun bindPumpWithConcentration(pumpWithConcentrationImpl: PumpWithConcentrationImpl): PumpWithConcentration
        @Binds fun bindPumpStatusGenerator(pumpStatusGeneratorImpl: PumpStatusProviderImpl): PumpStatusProvider

        @Binds fun bindTrendCalculatorInterface(trendCalculator: TrendCalculatorImpl): TrendCalculator
        @Binds fun bindTddCalculatorInterface(tddCalculator: TddCalculatorImpl): TddCalculator
        @Binds fun bindTirCalculatorInterface(tirCalculator: TirCalculatorImpl): TirCalculator
        @Binds fun bindDexcomTirCalculatorInterface(dexcomTirCalculator: DexcomTirCalculatorImpl): DexcomTirCalculator
        @Binds fun bindPumpSyncInterface(pumpSyncImplementation: PumpSyncImplementation): PumpSync
        @Binds fun bindLocalAlertUtilsInterface(localAlertUtils: LocalAlertUtilsImpl): LocalAlertUtils
        @Binds fun bindIconsProviderInterface(iconsProvider: IconsProviderImplementation): IconsProvider
        @Binds fun bindNotificationHolderInterface(notificationHolder: NotificationHolderImpl): NotificationHolder
        @Binds fun bindNotificationManager(notificationManagerImpl: NotificationManagerImpl): NotificationManager
        @Binds fun bindAlarmSoundPlayer(alarmSoundPlayerImpl: AlarmSoundPlayerImpl): AlarmSoundPlayer
        @Binds fun bindsProfileFunction(profileFunctionImpl: ProfileFunctionImpl): ProfileFunction
        @Binds fun bindsProfileUtil(profileUtilImpl: ProfileUtilImpl): ProfileUtil
        @Binds fun bindsProfileRepository(profileRepositoryImpl: ProfileRepositoryImpl): ProfileRepository
        @Binds fun bindsStorage(fileStorage: FileStorage): Storage
        @Binds fun bindsReceiverStatusStore(receiverStatusStoreImpl: ReceiverStatusStoreImpl): ReceiverStatusStore
        @Binds fun bindsUserEntryPresentationHelper(userEntryPresentationHelperImpl: UserEntryPresentationHelperImpl): UserEntryPresentationHelper
        @Binds fun bindsGlucoseStatusProvider(glucoseStatusProviderImpl: GlucoseStatusProviderImpl): GlucoseStatusProvider
        @Binds fun bindsDecimalFormatter(decimalFormatterImpl: DecimalFormatterImpl): DecimalFormatter
        @Binds fun bindsProfileStore(profileStoreObject: ProfileStoreObject): ProfileStore
        @Binds fun bindsAutosensData(autosensDataObject: AutosensDataObject): AutosensData
        @Binds fun bindsAPSResult(determineBasalResult: DetermineBasalResult): APSResult
        @Binds fun bindsPumpEnactResult(pumpEnactResultObject: PumpEnactResultObject): PumpEnactResult
    }
}