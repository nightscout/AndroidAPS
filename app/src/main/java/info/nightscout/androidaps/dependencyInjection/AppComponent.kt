package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.plugins.aps.openAPSMA.LoggerCallback
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.queue.commands.CommandSetProfile
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        AppModule::class,
        ReceiversModule::class,
        ServicesModule::class
    ]
)
interface AppComponent : AndroidInjector<MainApp> {

    fun injectCommandSetProfile(commandSetProfile: CommandSetProfile)

    fun injectObjective0(objective0: Objective0)
    fun injectObjective1(objective1: Objective1)
    fun injectObjective2(objective2: Objective2)
    fun injectObjective3(objective3: Objective3)
    fun injectObjective5(objective5: Objective5)
    fun injectObjective6(objective6: Objective6)

    fun injectAutomationEvent(automationEvent: AutomationEvent)

    fun injectTrigger(trigger: Trigger)
    fun injectTrigger(triggerAutosensValue: TriggerAutosensValue)
    fun injectTrigger(triggerBg: TriggerBg)
    fun injectTrigger(triggerBolusAgo: TriggerBolusAgo)
    fun injectTrigger(triggerCOB: TriggerCOB)
    fun injectTrigger(triggerConnector: TriggerConnector)
    fun injectTrigger(triggerDelta: TriggerDelta)
    fun injectTrigger(triggerDummy: TriggerDummy)
    fun injectTrigger(triggerIob: TriggerIob)
    fun injectTrigger(triggerLocation: TriggerLocation)
    fun injectTrigger(triggerProfilePercent: TriggerProfilePercent)
    fun injectTrigger(triggerPumpLastConnection: TriggerPumpLastConnection)
    fun injectTrigger(triggerRecurringTime: TriggerRecurringTime)
    fun injectTrigger(triggerTempTarget: TriggerTempTarget)
    fun injectTrigger(triggerTime: TriggerTime)
    fun injectTrigger(triggerTimeRange : TriggerTimeRange)
    fun injectTrigger(triggerWifiSsid: TriggerWifiSsid)

    fun injectAction(action: Action)
    fun injectActionDummy(action: ActionDummy)
    fun injectActionLoopDisable(action: ActionLoopDisable)
    fun injectActionLoopEnable(action: ActionLoopEnable)
    fun injectActionLoopResume(action: ActionLoopResume)
    fun injectAction(action: ActionLoopSuspend)
    fun injectActionLoopSuspend(action: ActionNotification)
    fun injectActionProfileSwitch(action: ActionProfileSwitch)
    fun injectAction(action: ActionProfileSwitchPercent)
    fun injectActionProfileSwitchPercent(action: ActionSendSMS)
    fun injectActionStartTempTarget(action: ActionStartTempTarget)
    fun injectActionStopTempTarget(action: ActionStopTempTarget)

    fun injectElement(element: Element)
    fun injectElement(comparator: Comparator)
    fun injectElement(comparatorExists: ComparatorExists)
    fun injectElement(inputBg: InputBg)
    fun injectElement(inputButton: InputButton)
    fun injectElement(inputDelta: InputDelta)
    fun injectElement(inputDouble: InputDouble)
    fun injectElement(inputDuration: InputDuration)
    fun injectElement(inputInsulin: InputInsulin)
    fun injectElement(inputLocationMode: InputLocationMode)
    fun injectElement(inputPercent: InputPercent)
    fun injectElement(inputProfileName: InputProfileName)
    fun injectElement(inputString: InputString)
    fun injectElement(inputTempTarget: InputTempTarget)
    fun injectElement(labelWithElement: LabelWithElement)
    fun injectElement(staticLabel: StaticLabel)

    fun injectTreatment(treatment: Treatment)
    fun injectBgReading(bgReading: BgReading)

    fun injectNotification(notificationWithAction: NotificationWithAction)

    fun injectLoggerCallback(loggerCallback: LoggerCallback)
    fun injectBolusWizard(bolusWizard: BolusWizard)
    fun injectQuickWizardEntry(quickWizardEntry: QuickWizardEntry)

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(mainApp: MainApp): Builder

        fun build(): AppComponent
    }
}