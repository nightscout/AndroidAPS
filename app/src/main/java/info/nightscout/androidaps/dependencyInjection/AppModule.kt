package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.ProfileStore
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.AAPSLoggerProduction
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalResultAMA
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs
import info.nightscout.androidaps.plugins.general.maintenance.formats.ClassicPrefsFormat
import info.nightscout.androidaps.plugins.general.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.general.smsCommunicator.AuthRequest
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobOref1Thread
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobThread
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.*
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.queue.commands.*
import info.nightscout.androidaps.setupwizard.SWEventListener
import info.nightscout.androidaps.setupwizard.SWScreen
import info.nightscout.androidaps.setupwizard.elements.*
import info.nightscout.androidaps.utils.CryptoUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.sharedPreferences.SPImplementation
import info.nightscout.androidaps.utils.storage.FileStorage
import info.nightscout.androidaps.utils.storage.Storage
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import javax.inject.Singleton

@Module(includes = [AppModule.AppBindings::class, PluginsModule::class])
open class AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context, resourceHelper: ResourceHelper): SP {
        return SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), resourceHelper)
    }

    @Provides
    @Singleton
    fun provideProfileFunction(injector: HasAndroidInjector, aapsLogger: AAPSLogger, sp: SP, resourceHelper: ResourceHelper, activePlugin: ActivePluginProvider, fabricPrivacy: FabricPrivacy): ProfileFunction {
        return ProfileFunctionImplementation(injector, aapsLogger, sp, resourceHelper, activePlugin, fabricPrivacy)
    }

    @Provides
    @Singleton
    fun provideResources(mainApp: MainApp): ResourceHelper {
        return ResourceHelperImplementation(mainApp)
    }

    @Provides
    @Singleton
    fun provideAAPSLogger(): AAPSLogger {
        return AAPSLoggerProduction()
/*        if (BuildConfig.DEBUG) {
            AAPSLoggerDebug()
        } else {
            AAPSLoggerProduction()
        }
 */
    }

    @Provides
    fun providesPlugins(@PluginsModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
                        @PluginsModule.PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.NSClient nsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
                        @PluginsModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>)
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (Config.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (Config.APS) plugins += aps.get()
        if (!Config.NSCLIENT) plugins += notNsClient.get()
        if (Config.NSCLIENT) plugins += nsClient.get()
        return plugins.toList().sortedBy { it.first }.map { it.second }
    }

    @Provides
    @Singleton
    fun provideStorage(): Storage {
        return FileStorage()
    }

    @Module
    interface AppBindings {

        @ContributesAndroidInjector fun profileStoreInjector(): ProfileStore

        @ContributesAndroidInjector fun pumpEnactResultInjector(): PumpEnactResult

        @ContributesAndroidInjector fun apsResultInjector(): APSResult
        @ContributesAndroidInjector fun determineBasalResultSMBInjector(): DetermineBasalResultSMB
        @ContributesAndroidInjector fun determineBasalResultAMAInjector(): DetermineBasalResultAMA

        @ContributesAndroidInjector
        fun determineBasalAdapterSMBJSInjector(): DetermineBasalAdapterSMBJS

        @ContributesAndroidInjector fun commandQueueInjector(): CommandQueue
        @ContributesAndroidInjector fun commandBolusInjector(): CommandBolus

        @ContributesAndroidInjector
        fun commandCancelExtendedBolusInjector(): CommandCancelExtendedBolus

        @ContributesAndroidInjector fun commandCancelTempBasalInjector(): CommandCancelTempBasal
        @ContributesAndroidInjector fun commandExtendedBolusInjector(): CommandExtendedBolus

        @ContributesAndroidInjector
        fun commandInsightSetTBROverNotificationInjector(): CommandInsightSetTBROverNotification

        @ContributesAndroidInjector fun commandLoadEventsInjector(): CommandLoadEvents
        @ContributesAndroidInjector fun commandLoadHistoryInjector(): CommandLoadHistory
        @ContributesAndroidInjector fun commandLoadTDDsInjector(): CommandLoadTDDs
        @ContributesAndroidInjector fun commandReadStatusInjector(): CommandReadStatus
        @ContributesAndroidInjector fun commandSetProfileInjector(): CommandSetProfile
        @ContributesAndroidInjector fun commandCommandSMBBolusInjector(): CommandSMBBolus
        @ContributesAndroidInjector fun commandStartPumpInjector(): CommandStartPump
        @ContributesAndroidInjector fun commandStopPumpInjector(): CommandStopPump
        @ContributesAndroidInjector fun commandTempBasalAbsoluteInjector(): CommandTempBasalAbsolute
        @ContributesAndroidInjector fun commandTempBasalPercentInjector(): CommandTempBasalPercent
        @ContributesAndroidInjector fun commandSetUserSettingsInjector(): CommandSetUserSettings

        @ContributesAndroidInjector fun objectiveInjector(): Objective
        @ContributesAndroidInjector fun objective0Injector(): Objective0
        @ContributesAndroidInjector fun objective1Injector(): Objective1
        @ContributesAndroidInjector fun objective2Injector(): Objective2
        @ContributesAndroidInjector fun objective3Injector(): Objective3
        @ContributesAndroidInjector fun objective4Injector(): Objective4
        @ContributesAndroidInjector fun objective5Injector(): Objective5
        @ContributesAndroidInjector fun objective6Injector(): Objective6
        @ContributesAndroidInjector fun objective7Injector(): Objective7
        @ContributesAndroidInjector fun objective8Injector(): Objective8
        @ContributesAndroidInjector fun objective9Injector(): Objective9
        @ContributesAndroidInjector fun objective10Injector(): Objective10

        @ContributesAndroidInjector fun automationEventInjector(): AutomationEvent

        @ContributesAndroidInjector fun triggerInjector(): Trigger
        @ContributesAndroidInjector fun triggerAutosensValueInjector(): TriggerAutosensValue
        @ContributesAndroidInjector fun triggerBgInjector(): TriggerBg
        @ContributesAndroidInjector fun triggerBolusAgoInjector(): TriggerBolusAgo
        @ContributesAndroidInjector fun triggerCOBInjector(): TriggerCOB
        @ContributesAndroidInjector fun triggerConnectorInjector(): TriggerConnector
        @ContributesAndroidInjector fun triggerDeltaInjector(): TriggerDelta
        @ContributesAndroidInjector fun triggerDummyInjector(): TriggerDummy
        @ContributesAndroidInjector fun triggerIobInjector(): TriggerIob
        @ContributesAndroidInjector fun triggerLocationInjector(): TriggerLocation
        @ContributesAndroidInjector fun triggerProfilePercentInjector(): TriggerProfilePercent

        @ContributesAndroidInjector
        fun triggerPumpLastConnectionInjector(): TriggerPumpLastConnection

        @ContributesAndroidInjector fun triggerBTDeviceInjector(): TriggerBTDevice
        @ContributesAndroidInjector fun triggerRecurringTimeInjector(): TriggerRecurringTime
        @ContributesAndroidInjector fun triggerTempTargetInjector(): TriggerTempTarget
        @ContributesAndroidInjector fun triggerTime(): TriggerTime
        @ContributesAndroidInjector fun triggerTimeRangeInjector(): TriggerTimeRange
        @ContributesAndroidInjector fun triggerWifiSsidInjector(): TriggerWifiSsid

        @ContributesAndroidInjector fun actionInjector(): Action
        @ContributesAndroidInjector fun actionLoopDisableInjector(): ActionLoopDisable
        @ContributesAndroidInjector fun actionLoopEnableInjector(): ActionLoopEnable
        @ContributesAndroidInjector fun actionLoopResumeInjector(): ActionLoopResume
        @ContributesAndroidInjector fun actionLoopSuspendInjector(): ActionLoopSuspend
        @ContributesAndroidInjector fun actionNotificationInjector(): ActionNotification
        @ContributesAndroidInjector fun actionProfileSwitchInjector(): ActionProfileSwitch

        @ContributesAndroidInjector
        fun actionProfileSwitchPercentInjector(): ActionProfileSwitchPercent

        @ContributesAndroidInjector fun actionSendSMSInjector(): ActionSendSMS
        @ContributesAndroidInjector fun actionStartTempTargetInjector(): ActionStartTempTarget
        @ContributesAndroidInjector fun actionStopTempTargetInjector(): ActionStopTempTarget
        @ContributesAndroidInjector fun actionDummyInjector(): ActionDummy

        @ContributesAndroidInjector fun elementInjector(): Element
        @ContributesAndroidInjector fun inputBgInjector(): InputBg
        @ContributesAndroidInjector fun inputButtonInjector(): InputButton
        @ContributesAndroidInjector fun comparatorInjector(): Comparator
        @ContributesAndroidInjector fun comparatorConnectInjector(): ComparatorConnect
        @ContributesAndroidInjector fun comparatorExistsInjector(): ComparatorExists
        @ContributesAndroidInjector fun inputDateTimeInjector(): InputDateTime
        @ContributesAndroidInjector fun inputDeltaInjector(): InputDelta
        @ContributesAndroidInjector fun inputDoubleInjector(): InputDouble
        @ContributesAndroidInjector fun inputDropdownMenuInjector(): InputDropdownMenu
        @ContributesAndroidInjector fun inputDurationInjector(): InputDuration
        @ContributesAndroidInjector fun inputInsulinInjector(): InputInsulin
        @ContributesAndroidInjector fun inputLocationModeInjector(): InputLocationMode
        @ContributesAndroidInjector fun inputPercentInjector(): InputPercent
        @ContributesAndroidInjector fun inputProfileNameInjector(): InputProfileName
        @ContributesAndroidInjector fun inputStringInjector(): InputString
        @ContributesAndroidInjector fun inputTempTargetInjector(): InputTempTarget
        @ContributesAndroidInjector fun inputTimeRangeInjector(): InputTimeRange
        @ContributesAndroidInjector fun inputTimeInjector(): InputTime
        @ContributesAndroidInjector fun inputWeekDayInjector(): InputWeekDay
        @ContributesAndroidInjector fun labelWithElementInjector(): LabelWithElement
        @ContributesAndroidInjector fun staticLabelInjector(): StaticLabel

        @ContributesAndroidInjector fun autosensDataInjector(): AutosensData
        @ContributesAndroidInjector fun iobCobThreadInjector(): IobCobThread
        @ContributesAndroidInjector fun iobCobOref1ThreadInjector(): IobCobOref1Thread

        @ContributesAndroidInjector fun bgReadingInjector(): BgReading
        @ContributesAndroidInjector fun treatmentInjector(): Treatment
        @ContributesAndroidInjector fun profileSwitchInjector(): ProfileSwitch
        @ContributesAndroidInjector fun temporaryBasalInjector(): TemporaryBasal
        @ContributesAndroidInjector fun careportalEventInjector(): CareportalEvent

        @ContributesAndroidInjector fun notificationWithActionInjector(): NotificationWithAction

        @ContributesAndroidInjector fun loggerCallbackInjector(): LoggerCallback
        @ContributesAndroidInjector fun loggerBolusWizard(): BolusWizard
        @ContributesAndroidInjector fun loggerQuickWizardEntry(): QuickWizardEntry

        @ContributesAndroidInjector fun authRequestInjector(): AuthRequest

        @ContributesAndroidInjector fun swBreakInjector(): SWBreak
        @ContributesAndroidInjector fun swButtonInjector(): SWButton
        @ContributesAndroidInjector fun swEditNumberWithUnitsInjector(): SWEditNumberWithUnits
        @ContributesAndroidInjector fun swEditStringInjector(): SWEditString
        @ContributesAndroidInjector fun swEditEncryptedPasswordInjector(): SWEditEncryptedPassword
        @ContributesAndroidInjector fun swEditUrlInjector(): SWEditUrl
        @ContributesAndroidInjector fun swFragmentInjector(): SWFragment
        @ContributesAndroidInjector fun swHtmlLinkInjector(): SWHtmlLink
        @ContributesAndroidInjector fun swInfotextInjector(): SWInfotext
        @ContributesAndroidInjector fun swItemInjector(): SWItem
        @ContributesAndroidInjector fun swPluginInjector(): SWPlugin
        @ContributesAndroidInjector fun swRadioButtonInjector(): SWRadioButton
        @ContributesAndroidInjector fun swScreenInjector(): SWScreen
        @ContributesAndroidInjector fun swEventListenerInjector(): SWEventListener

        @ContributesAndroidInjector fun profileInjector(): Profile
        @ContributesAndroidInjector fun glucoseStatusInjector(): GlucoseStatus

        @ContributesAndroidInjector fun graphDataInjector(): GraphData

        @ContributesAndroidInjector fun cryptoUtilInjector(): CryptoUtil
        @ContributesAndroidInjector fun importExportPrefsInjector(): ImportExportPrefs
        @ContributesAndroidInjector fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat
        @ContributesAndroidInjector fun classicPrefsFormatInjector(): ClassicPrefsFormat

        @Binds fun bindContext(mainApp: MainApp): Context
        @Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector

        // Medtronic
        @ContributesAndroidInjector fun rileyLinkCommunicationManagerProvider(): RileyLinkCommunicationManager
        @ContributesAndroidInjector fun medtronicCommunicationManagerProvider(): MedtronicCommunicationManager
        @ContributesAndroidInjector fun medtronicUITaskProvider(): MedtronicUITask
        @ContributesAndroidInjector fun serviceTaskProvider(): ServiceTask
        @ContributesAndroidInjector fun pumpTaskProvider(): PumpTask
        @ContributesAndroidInjector fun discoverGattServicesTaskProvider(): DiscoverGattServicesTask
        @ContributesAndroidInjector fun initializePumpManagerTaskProvider(): InitializePumpManagerTask
        @ContributesAndroidInjector fun resetRileyLinkConfigurationTaskProvider(): ResetRileyLinkConfigurationTask
        @ContributesAndroidInjector fun wakeAndTuneTaskProvider(): WakeAndTuneTask
        @ContributesAndroidInjector fun radioResponseProvider(): RadioResponse
        @ContributesAndroidInjector fun rileyLinkBLEProvider(): RileyLinkBLE

        @Binds
        fun bindActivePluginProvider(pluginStore: PluginStore): ActivePluginProvider

        @Binds fun commandQueueProvider(commandQueue: CommandQueue): CommandQueueProvider

    }
}
