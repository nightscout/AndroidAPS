package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.core.di.CoreModule
import info.nightscout.androidaps.dana.di.DanaModule
import info.nightscout.androidaps.danar.di.DanaRModule
import info.nightscout.androidaps.danars.di.DanaRSModule
import info.nightscout.androidaps.plugins.pump.common.dagger.RileyLinkModule
import info.nightscout.androidaps.plugins.pump.omnipod.dagger.OmnipodModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        PluginsModule::class,
        SkinsModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        AppModule::class,
        ReceiversModule::class,
        ServicesModule::class,
        AutomationModule::class,
        CommandQueueModule::class,
        ObjectivesModule::class,
        WizardModule::class,
        RileyLinkModule::class,
        MedtronicModule::class,
        OmnipodModule::class,
        APSModule::class,
        PreferencesModule::class,
        OverviewModule::class,
        DataClassesModule::class,
        SMSModule::class,
        UIModule::class,
        CoreModule::class,
        DanaModule::class,
        DanaRModule::class,
        DanaRSModule::class,
        OHUploaderModule::class
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