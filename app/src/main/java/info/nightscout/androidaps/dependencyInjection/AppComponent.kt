package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.core.dependencyInjection.CoreModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        CoreModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        AppModule::class,
        ReceiversModule::class,
        ServicesModule::class,
        AutomationModule::class,
        CommandQueueModule::class,
        ObjectivesModule::class,
        WizardModule::class,
        MedtronicModule::class,
        APSModule::class,
        PreferencesModule::class,
        OverviewModule::class,
        DataClassesModule::class,
        SMSModule::class,
        UIModule::class
    ]
)
interface AppComponent : AndroidInjector<MainApp> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(mainApp: MainApp): Builder

        fun build(): AppComponent
    }
}