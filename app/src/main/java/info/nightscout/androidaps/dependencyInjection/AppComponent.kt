package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.aps.openAPSMA.LoggerCallback
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS
import info.nightscout.androidaps.queue.commands.CommandSetProfile
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

    fun injectActionSendSMS(actionSendSMS: ActionSendSMS)

    fun injectObjective0(objective0: Objective0)
    fun injectObjective1(objective1: Objective1)
    fun injectObjective2(objective2: Objective2)
    fun injectObjective3(objective3: Objective3)
    fun injectObjective5(objective5: Objective5)
    fun injectObjective6(objective6: Objective6)

    fun injectLoggerCallback(loggerCallback: LoggerCallback)

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(mainApp: MainApp): Builder

        fun build(): AppComponent
    }
}