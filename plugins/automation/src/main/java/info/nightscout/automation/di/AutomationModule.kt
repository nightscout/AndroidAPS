package info.nightscout.automation.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.automation.AutomationEventObject
import info.nightscout.automation.AutomationFragment
import info.nightscout.automation.AutomationPlugin
import info.nightscout.automation.actions.Action
import info.nightscout.automation.actions.ActionAlarm
import info.nightscout.automation.actions.ActionCarePortalEvent
import info.nightscout.automation.actions.ActionDummy
import info.nightscout.automation.actions.ActionLoopDisable
import info.nightscout.automation.actions.ActionLoopEnable
import info.nightscout.automation.actions.ActionLoopResume
import info.nightscout.automation.actions.ActionLoopSuspend
import info.nightscout.automation.actions.ActionNotification
import info.nightscout.automation.actions.ActionProfileSwitch
import info.nightscout.automation.actions.ActionProfileSwitchPercent
import info.nightscout.automation.actions.ActionRunAutotune
import info.nightscout.automation.actions.ActionSendSMS
import info.nightscout.automation.actions.ActionStartTempTarget
import info.nightscout.automation.actions.ActionStopProcessing
import info.nightscout.automation.actions.ActionStopTempTarget
import info.nightscout.automation.dialogs.ChooseActionDialog
import info.nightscout.automation.dialogs.ChooseOperationDialog
import info.nightscout.automation.dialogs.ChooseTriggerDialog
import info.nightscout.automation.dialogs.EditActionDialog
import info.nightscout.automation.dialogs.EditEventDialog
import info.nightscout.automation.dialogs.EditTriggerDialog
import info.nightscout.automation.triggers.Trigger
import info.nightscout.automation.triggers.TriggerAutosensValue
import info.nightscout.automation.triggers.TriggerBTDevice
import info.nightscout.automation.triggers.TriggerBg
import info.nightscout.automation.triggers.TriggerBolusAgo
import info.nightscout.automation.triggers.TriggerCOB
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerDelta
import info.nightscout.automation.triggers.TriggerDummy
import info.nightscout.automation.triggers.TriggerIob
import info.nightscout.automation.triggers.TriggerLocation
import info.nightscout.automation.triggers.TriggerProfilePercent
import info.nightscout.automation.triggers.TriggerPumpLastConnection
import info.nightscout.automation.triggers.TriggerRecurringTime
import info.nightscout.automation.triggers.TriggerTempTarget
import info.nightscout.automation.triggers.TriggerTempTargetValue
import info.nightscout.automation.triggers.TriggerTime
import info.nightscout.automation.triggers.TriggerTimeRange
import info.nightscout.automation.triggers.TriggerWifiSsid
import info.nightscout.interfaces.automation.Automation

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
    @ContributesAndroidInjector abstract fun triggerCOBInjector(): TriggerCOB
    @ContributesAndroidInjector abstract fun triggerConnectorInjector(): TriggerConnector
    @ContributesAndroidInjector abstract fun triggerDeltaInjector(): TriggerDelta
    @ContributesAndroidInjector abstract fun triggerDummyInjector(): TriggerDummy
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

    @ContributesAndroidInjector abstract fun actionInjector(): Action
    @ContributesAndroidInjector abstract fun actionStopProcessingInjector(): ActionStopProcessing
    @ContributesAndroidInjector abstract fun actionLoopDisableInjector(): ActionLoopDisable
    @ContributesAndroidInjector abstract fun actionLoopEnableInjector(): ActionLoopEnable
    @ContributesAndroidInjector abstract fun actionLoopResumeInjector(): ActionLoopResume
    @ContributesAndroidInjector abstract fun actionLoopSuspendInjector(): ActionLoopSuspend
    @ContributesAndroidInjector abstract fun actionNotificationInjector(): ActionNotification
    @ContributesAndroidInjector abstract fun actionAlarmInjector(): ActionAlarm
    @ContributesAndroidInjector abstract fun actionCarePortalEventInjector(): ActionCarePortalEvent
    @ContributesAndroidInjector abstract fun actionProfileSwitchInjector(): ActionProfileSwitch
    @ContributesAndroidInjector abstract fun actionProfileSwitchPercentInjector(): ActionProfileSwitchPercent
    @ContributesAndroidInjector abstract fun actionRunAutotuneInjector(): ActionRunAutotune
    @ContributesAndroidInjector abstract fun actionSendSMSInjector(): ActionSendSMS
    @ContributesAndroidInjector abstract fun actionStartTempTargetInjector(): ActionStartTempTarget
    @ContributesAndroidInjector abstract fun actionStopTempTargetInjector(): ActionStopTempTarget
    @ContributesAndroidInjector abstract fun actionDummyInjector(): ActionDummy

    @Module
    interface Bindings {

        @Binds fun bindAutomation(automationPlugin: AutomationPlugin): Automation
    }
}