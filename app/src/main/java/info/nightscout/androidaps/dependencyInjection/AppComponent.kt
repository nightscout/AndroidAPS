package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.plugins.aps.openAPSMA.LoggerCallback
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.actions.*
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