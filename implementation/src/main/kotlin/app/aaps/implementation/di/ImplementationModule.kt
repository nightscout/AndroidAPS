package app.aaps.implementation.di

import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
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
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.alerts.LocalAlertUtilsImpl
import app.aaps.implementation.androidNotification.AlarmSoundPlayerImpl
import app.aaps.implementation.androidNotification.NotificationHolderImpl
import app.aaps.implementation.aps.DetermineBasalResult
import app.aaps.implementation.db.ProcessedTbrEbDataImpl
import app.aaps.implementation.insulin.ConcentrationHelperImpl
import app.aaps.implementation.iob.AutosensDataObject
import app.aaps.implementation.iob.GlucoseStatusProviderImpl
import app.aaps.implementation.locale.LocaleDependentSettingImpl
import app.aaps.implementation.logging.LoggerUtilsImpl
import app.aaps.implementation.logging.UserEntryLoggerImpl
import app.aaps.implementation.notifications.NotificationManagerImpl
import app.aaps.implementation.overview.LastBgDataImpl
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.implementation.profiling.ProfilerImpl
import app.aaps.implementation.protection.ExportPasswordDataStoreImpl
import app.aaps.implementation.protection.PasswordCheckImpl
import app.aaps.implementation.protection.SecureEncryptImpl
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.implementation.pump.PumpStatusProviderImpl
import app.aaps.implementation.pump.PumpWithConcentrationImpl
import app.aaps.implementation.resources.ResourceHelperImpl
import app.aaps.implementation.sharedPreferences.PreferencesImpl
import app.aaps.implementation.userEntry.UserEntryPresentationHelperImpl
import app.aaps.implementation.utils.fabric.FabricPrivacyImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

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

    /**
     * [BolusProgressData] is a plain class rather than an `@Inject constructor` one, because
     * `javax.inject` cannot be used from code that is meant to reach commonMain. Providing it here
     * keeps the graph identical: same singleton scope, same application-scoped CoroutineScope.
     */
    @Provides
    @Singleton
    fun provideBolusProgressData(
        ch: ConcentrationHelper,
        @ApplicationScope appScope: CoroutineScope
    ): BolusProgressData = BolusProgressData(ch, appScope)

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        // NetworkChangeReceiver, BTReceiver and ChargingStateReceiver moved to Metro - see
        // AppReceiversGraph. They no longer extend a dagger.android base class, so leaving them here
        // would only generate an injector that injects nothing.
        // TimeDateOrTZChangeReceiver moved to Metro - see AppReceiversGraph. It was the last
        // dagger.android receiver in this module.

        @Binds fun bindPreferences(preferencesImpl: PreferencesImpl): Preferences

        @Binds fun bindFabricPrivacy(fabricPrivacyImpl: FabricPrivacyImpl): FabricPrivacy
        @Binds fun bindActivePlugin(pluginStore: PluginStore): ActivePlugin
        @Binds fun bindPluginPermissions(pluginStore: PluginStore): PluginPermissions

        // Runtime-permission sources for non-plugin features (e.g. standalone Automation).
        // May be empty; contributors bind via @IntoSet PermissionProvider.
        @Multibinds fun permissionProviders(): Set<PermissionProvider>
        @Binds fun bindResourceHelper(resourceHelperImpl: ResourceHelperImpl): ResourceHelper
        @Binds fun bindPumpWithConcentration(pumpWithConcentrationImpl: PumpWithConcentrationImpl): PumpWithConcentration

        @Binds fun bindLocalAlertUtilsInterface(localAlertUtils: LocalAlertUtilsImpl): LocalAlertUtils
        @Binds fun bindNotificationManager(notificationManagerImpl: NotificationManagerImpl): NotificationManager
        @Binds fun bindsProfileFunction(profileFunctionImpl: ProfileFunctionImpl): ProfileFunction
        @Binds fun bindsAutosensData(autosensDataObject: AutosensDataObject): AutosensData
        @Binds fun bindsAPSResult(determineBasalResult: DetermineBasalResult): APSResult
        @Binds fun bindsPumpEnactResult(pumpEnactResultObject: PumpEnactResultObject): PumpEnactResult
    }
}