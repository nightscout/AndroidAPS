package app.aaps.plugins.automation.di

import app.aaps.core.interfaces.automation.Automation
import app.aaps.plugins.automation.AutomationEventObject
import app.aaps.plugins.automation.AutomationFragment
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionDummy
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.actions.ActionStopTempTarget
import app.aaps.plugins.automation.dialogs.ChooseActionDialog
import app.aaps.plugins.automation.dialogs.ChooseOperationDialog
import app.aaps.plugins.automation.dialogs.ChooseTriggerDialog
import app.aaps.plugins.automation.dialogs.EditActionDialog
import app.aaps.plugins.automation.dialogs.EditEventDialog
import app.aaps.plugins.automation.dialogs.EditTriggerDialog
import app.aaps.plugins.automation.services.LocationService
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerAutosensValue
import app.aaps.plugins.automation.triggers.TriggerBTDevice
import app.aaps.plugins.automation.triggers.TriggerBg
import app.aaps.plugins.automation.triggers.TriggerBolusAgo
import app.aaps.plugins.automation.triggers.TriggerCOB
import app.aaps.plugins.automation.triggers.TriggerCannulaAge
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDelta
import app.aaps.plugins.automation.triggers.TriggerDummy
import app.aaps.plugins.automation.triggers.TriggerHeartRate
import app.aaps.plugins.automation.triggers.TriggerInsulinAge
import app.aaps.plugins.automation.triggers.TriggerIob
import app.aaps.plugins.automation.triggers.TriggerLocation
import app.aaps.plugins.automation.triggers.TriggerPodChange
import app.aaps.plugins.automation.triggers.TriggerProfilePercent
import app.aaps.plugins.automation.triggers.TriggerPumpBatteryAge
import app.aaps.plugins.automation.triggers.TriggerPumpBatteryLevel
import app.aaps.plugins.automation.triggers.TriggerPumpLastConnection
import app.aaps.plugins.automation.triggers.TriggerRecurringTime
import app.aaps.plugins.automation.triggers.TriggerReservoirLevel
import app.aaps.plugins.automation.triggers.TriggerSensorAge
import app.aaps.plugins.automation.triggers.TriggerStepsCount
import app.aaps.plugins.automation.triggers.TriggerTempTarget
import app.aaps.plugins.automation.triggers.TriggerTempTargetValue
import app.aaps.plugins.automation.triggers.TriggerTime
import app.aaps.plugins.automation.triggers.TriggerTimeRange
import app.aaps.plugins.automation.triggers.TriggerWifiSsid
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        AutomationModule.Bindings::class
    ]
)
@Suppress("unused")
abstract class AutomationModule {

    @ContributesAndroidInjector abstract fun contributesAutomationFragment(): AutomationFragment
    @ContributesAndroidInjector abstract fun contributesChooseActionDialog(): ChooseActionDialog
    @ContributesAndroidInjector abstract fun contributesChooseTriggerDialog(): ChooseTriggerDialog
    @ContributesAndroidInjector abstract fun contributesChooseOperationDialog(): ChooseOperationDialog
    @ContributesAndroidInjector abstract fun contributesEditActionDialog(): EditActionDialog
    @ContributesAndroidInjector abstract fun contributesEditEventDialog(): EditEventDialog
    @ContributesAndroidInjector abstract fun contributesEditTriggerDialog(): EditTriggerDialog
    @ContributesAndroidInjector abstract fun automationEventInjector(): AutomationEventObject

    @ContributesAndroidInjector abstract fun triggerInjector(): Trigger
    @ContributesAndroidInjector abstract fun triggerAutosensValueInjector(): TriggerAutosensValue
    @ContributesAndroidInjector abstract fun triggerBgInjector(): TriggerBg
    @ContributesAndroidInjector abstract fun triggerBolusAgoInjector(): TriggerBolusAgo
    @ContributesAndroidInjector abstract fun triggerSensorAgeInjector(): TriggerSensorAge
    @ContributesAndroidInjector abstract fun triggerPadChangeInjector(): TriggerPodChange
    @ContributesAndroidInjector abstract fun triggerCannulaAgeInjector(): TriggerCannulaAge
    @ContributesAndroidInjector abstract fun triggerInsulinAgeInjector(): TriggerInsulinAge
    @ContributesAndroidInjector abstract fun triggerReservoirLevelInjector(): TriggerReservoirLevel
    @ContributesAndroidInjector abstract fun triggerPumpBatteryAgeInjector(): TriggerPumpBatteryAge
    @ContributesAndroidInjector abstract fun triggerPumpBatteryLevelInjector(): TriggerPumpBatteryLevel
    @ContributesAndroidInjector abstract fun triggerCOBInjector(): TriggerCOB
    @ContributesAndroidInjector abstract fun triggerConnectorInjector(): TriggerConnector
    @ContributesAndroidInjector abstract fun triggerDeltaInjector(): TriggerDelta
    @ContributesAndroidInjector abstract fun triggerDummyInjector(): TriggerDummy
    @ContributesAndroidInjector abstract fun triggerHeartRateInjector(): TriggerHeartRate
    @ContributesAndroidInjector abstract fun triggerIobInjector(): TriggerIob
    @ContributesAndroidInjector abstract fun triggerLocationInjector(): TriggerLocation
    @ContributesAndroidInjector abstract fun triggerProfilePercentInjector(): TriggerProfilePercent
    @ContributesAndroidInjector abstract fun triggerPumpLastConnectionInjector(): TriggerPumpLastConnection
    @ContributesAndroidInjector abstract fun triggerBTDeviceInjector(): TriggerBTDevice
    @ContributesAndroidInjector abstract fun triggerRecurringTimeInjector(): TriggerRecurringTime
    @ContributesAndroidInjector abstract fun triggerTempTargetInjector(): TriggerTempTarget
    @ContributesAndroidInjector abstract fun triggerTempTargetValueInjector(): TriggerTempTargetValue
    @ContributesAndroidInjector abstract fun triggerTime(): TriggerTime
    @ContributesAndroidInjector abstract fun triggerTimeRangeInjector(): TriggerTimeRange
    @ContributesAndroidInjector abstract fun triggerWifiSsidInjector(): TriggerWifiSsid
    @ContributesAndroidInjector abstract fun triggerStepsCountInjector(): TriggerStepsCount

    @ContributesAndroidInjector abstract fun actionInjector(): Action
    @ContributesAndroidInjector abstract fun actionSMBChangeInjector(): ActionSMBChange
    @ContributesAndroidInjector abstract fun actionStopProcessingInjector(): ActionStopProcessing
    @ContributesAndroidInjector abstract fun actionNotificationInjector(): ActionNotification
    @ContributesAndroidInjector abstract fun actionAlarmInjector(): ActionAlarm
    @ContributesAndroidInjector abstract fun actionSettingsExportInjector(): ActionSettingsExport
    @ContributesAndroidInjector abstract fun actionCarePortalEventInjector(): ActionCarePortalEvent
    @ContributesAndroidInjector abstract fun actionProfileSwitchInjector(): ActionProfileSwitch
    @ContributesAndroidInjector abstract fun actionProfileSwitchPercentInjector(): ActionProfileSwitchPercent
    @ContributesAndroidInjector abstract fun actionRunAutotuneInjector(): ActionRunAutotune
    @ContributesAndroidInjector abstract fun actionSendSMSInjector(): ActionSendSMS
    @ContributesAndroidInjector abstract fun actionStartTempTargetInjector(): ActionStartTempTarget
    @ContributesAndroidInjector abstract fun actionStopTempTargetInjector(): ActionStopTempTarget
    @ContributesAndroidInjector abstract fun actionDummyInjector(): ActionDummy
    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService

    @Module
    interface Bindings {

        @Binds fun bindAutomation(automationPlugin: AutomationPlugin): Automation
    }
}