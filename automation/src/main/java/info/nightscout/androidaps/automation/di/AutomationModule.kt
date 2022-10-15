package info.nightscout.androidaps.automation.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*

@Module
@Suppress("unused")
abstract class AutomationModule {
    @ContributesAndroidInjector abstract fun automationEventInjector(): AutomationEvent

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
}