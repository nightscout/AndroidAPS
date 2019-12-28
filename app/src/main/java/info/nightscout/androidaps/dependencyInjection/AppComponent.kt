package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective5
import info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS
import info.nightscout.androidaps.queue.commands.CommandSetProfile
import info.nightscout.androidaps.services.DataService
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        AppModule::class
    ]
)
interface AppComponent : AndroidInjector<MainApp> {

    fun injectDataService(service: DataService)

    fun injectCommandSetProfile(commandSetProfile: CommandSetProfile)

    fun injectActionSendSMS(actionSendSMS: ActionSendSMS)

    fun injectObjective5(objective5: Objective5)

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(mainApp: MainApp): Builder

        fun build(): AppComponent
    }
}