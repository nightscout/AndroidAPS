package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.queue.commands.*

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
    @ContributesAndroidInjector abstract fun triggerTime(): TriggerTime
    @ContributesAndroidInjector abstract fun triggerTimeRangeInjector(): TriggerTimeRange
    @ContributesAndroidInjector abstract fun triggerWifiSsidInjector(): TriggerWifiSsid

    @ContributesAndroidInjector abstract fun actionInjector(): Action
    @ContributesAndroidInjector abstract fun actionLoopDisableInjector(): ActionLoopDisable
    @ContributesAndroidInjector abstract fun actionLoopEnableInjector(): ActionLoopEnable
    @ContributesAndroidInjector abstract fun actionLoopResumeInjector(): ActionLoopResume
    @ContributesAndroidInjector abstract fun actionLoopSuspendInjector(): ActionLoopSuspend
    @ContributesAndroidInjector abstract fun actionNotificationInjector(): ActionNotification
    @ContributesAndroidInjector abstract fun actionAlarmInjector(): ActionAlarm
    @ContributesAndroidInjector abstract fun actionProfileSwitchInjector(): ActionProfileSwitch
    @ContributesAndroidInjector abstract fun actionProfileSwitchPercentInjector(): ActionProfileSwitchPercent
    @ContributesAndroidInjector abstract fun actionSendSMSInjector(): ActionSendSMS
    @ContributesAndroidInjector abstract fun actionStartTempTargetInjector(): ActionStartTempTarget
    @ContributesAndroidInjector abstract fun actionStopTempTargetInjector(): ActionStopTempTarget
    @ContributesAndroidInjector abstract fun actionDummyInjector(): ActionDummy

    @ContributesAndroidInjector abstract fun elementInjector(): Element
    @ContributesAndroidInjector abstract fun inputBgInjector(): InputBg
    @ContributesAndroidInjector abstract fun inputButtonInjector(): InputButton
    @ContributesAndroidInjector abstract fun comparatorInjector(): Comparator
    @ContributesAndroidInjector abstract fun comparatorConnectInjector(): ComparatorConnect
    @ContributesAndroidInjector abstract fun comparatorExistsInjector(): ComparatorExists
    @ContributesAndroidInjector abstract fun inputDateTimeInjector(): InputDateTime
    @ContributesAndroidInjector abstract fun inputDeltaInjector(): InputDelta
    @ContributesAndroidInjector abstract fun inputDoubleInjector(): InputDouble
    @ContributesAndroidInjector abstract fun inputDropdownMenuInjector(): InputDropdownMenu
    @ContributesAndroidInjector abstract fun inputDurationInjector(): InputDuration
    @ContributesAndroidInjector abstract fun inputInsulinInjector(): InputInsulin
    @ContributesAndroidInjector abstract fun inputLocationModeInjector(): InputLocationMode
    @ContributesAndroidInjector abstract fun inputPercentInjector(): InputPercent
    @ContributesAndroidInjector abstract fun inputProfileNameInjector(): InputProfileName
    @ContributesAndroidInjector abstract fun inputStringInjector(): InputString
    @ContributesAndroidInjector abstract fun inputTempTargetInjector(): InputTempTarget
    @ContributesAndroidInjector abstract fun inputTimeRangeInjector(): InputTimeRange
    @ContributesAndroidInjector abstract fun inputTimeInjector(): InputTime
    @ContributesAndroidInjector abstract fun inputWeekDayInjector(): InputWeekDay
    @ContributesAndroidInjector abstract fun labelWithElementInjector(): LabelWithElement
    @ContributesAndroidInjector abstract fun staticLabelInjector(): StaticLabel
}