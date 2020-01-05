package info.nightscout.androidaps.dependencyInjection

import android.content.Context
import android.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.AAPSLoggerDebug
import info.nightscout.androidaps.logging.AAPSLoggerProduction
import info.nightscout.androidaps.plugins.aps.openAPSMA.LoggerCallback
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctionImplementation
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.queue.commands.CommandSetProfile
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.sharedPreferences.SPImplementation
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import javax.inject.Singleton

@Module(includes = [AppModule.AppBindings::class])
open class AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context, resourceHelper: ResourceHelper): SP {
        return SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), resourceHelper)
    }

    @Provides
    @Singleton
    fun provideProfileFunction(sp: SP, configBuilderPlugin: ConfigBuilderPlugin): ProfileFunction {
        return ProfileFunctionImplementation(sp, configBuilderPlugin)
    }

    @Provides
    @Singleton
    fun provideResources(mainApp: MainApp): ResourceHelper {
        return ResourceHelperImplementation(mainApp)
    }

    @Provides
    @Singleton
    fun provideAAPSLogger(): AAPSLogger {
        return if (BuildConfig.DEBUG) {
            AAPSLoggerDebug()
        } else {
            AAPSLoggerProduction()
        }
    }

    @Module
    interface AppBindings {

        @ContributesAndroidInjector fun commandSetProfileInjector(): CommandSetProfile
        @ContributesAndroidInjector fun objective0Injector(): Objective0
        @ContributesAndroidInjector fun objective1Injector(): Objective1
        @ContributesAndroidInjector fun objective2Injector(): Objective2
        @ContributesAndroidInjector fun objective3Injector(): Objective3
        @ContributesAndroidInjector fun objective5Injector(): Objective5
        @ContributesAndroidInjector fun objective6Injector(): Objective6

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
        @ContributesAndroidInjector fun triggerPumpLastConnectonInjector(): TriggerPumpLastConnection
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
        @ContributesAndroidInjector fun actionProfileSwitchPercentInjector(): ActionProfileSwitchPercent
        @ContributesAndroidInjector fun actionSendSMSInjector(): ActionSendSMS
        @ContributesAndroidInjector fun actionStartTempTargetInjector(): ActionStartTempTarget
        @ContributesAndroidInjector fun actionStopTempTargetInjector(): ActionStopTempTarget
        @ContributesAndroidInjector fun actionDummyInjector(): ActionDummy

        @ContributesAndroidInjector fun elementInjector(): Element
        @ContributesAndroidInjector fun comparatorInjector(): Comparator
        @ContributesAndroidInjector fun comparatorExistsInjector(): ComparatorExists
        @ContributesAndroidInjector fun inputBgInjector(): InputBg
        @ContributesAndroidInjector fun inputButtonInjector(): InputButton
        @ContributesAndroidInjector fun inputDeltaInjector(): InputDelta
        @ContributesAndroidInjector fun inputDoubleInjector(): InputDouble
        @ContributesAndroidInjector fun inputDurationInjector(): InputDuration
        @ContributesAndroidInjector fun inputInsulinInjector(): InputInsulin
        @ContributesAndroidInjector fun inputLocationModeInjector(): InputLocationMode
        @ContributesAndroidInjector fun inputPercentInjector(): InputPercent
        @ContributesAndroidInjector fun inputProfileNameInjector(): InputProfileName
        @ContributesAndroidInjector fun inputStringInjector(): InputString
        @ContributesAndroidInjector fun inputTempTargetInjector(): InputTempTarget
        @ContributesAndroidInjector fun labelWithElementInjector(): LabelWithElement
        @ContributesAndroidInjector fun staticLabelInjector(): StaticLabel

        @ContributesAndroidInjector fun bgReadingInjector(): BgReading
        @ContributesAndroidInjector fun treatmentInjector(): Treatment

        @ContributesAndroidInjector fun notificationWithActionInjector(): NotificationWithAction

        @ContributesAndroidInjector fun loggerCallbackInjector(): LoggerCallback
        @ContributesAndroidInjector fun loggerBolusWizard(): BolusWizard
        @ContributesAndroidInjector fun loggerQuickWizardEntry(): QuickWizardEntry

        @Binds fun bindContext(mainApp: MainApp): Context
    }
}
