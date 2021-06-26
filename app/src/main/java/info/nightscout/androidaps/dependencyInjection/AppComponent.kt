package info.nightscout.androidaps.dependencyInjection

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.automation.di.AutomationModule
import info.nightscout.androidaps.combo.di.ComboModule
import info.nightscout.androidaps.dana.di.DanaHistoryModule
import info.nightscout.androidaps.dana.di.DanaModule
import info.nightscout.androidaps.danar.di.DanaRModule
import info.nightscout.androidaps.danars.di.DanaRSModule
import info.nightscout.androidaps.database.DatabaseModule
import info.nightscout.androidaps.di.CoreModule
import info.nightscout.androidaps.diaconn.di.DiaconnG8Module
import info.nightscout.androidaps.insight.di.InsightDatabaseModule
import info.nightscout.androidaps.insight.di.InsightModule
import info.nightscout.androidaps.plugins.pump.common.di.PumpCommonModule
import info.nightscout.androidaps.plugins.pump.common.di.RileyLinkModule
import info.nightscout.androidaps.plugins.pump.medtronic.di.MedtronicModule
import info.nightscout.androidaps.plugins.pump.omnipod.dash.dagger.OmnipodDashModule
import info.nightscout.androidaps.plugins.pump.omnipod.eros.dagger.OmnipodErosModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        DatabaseModule::class,
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
        PumpCommonModule::class,
        RileyLinkModule::class,
        MedtronicModule::class,
        OmnipodDashModule::class,
        OmnipodErosModule::class,
        APSModule::class,
        PreferencesModule::class,
        OverviewModule::class,
        DataClassesModule::class,
        SMSModule::class,
        UIModule::class,
        CoreModule::class,
        DanaModule::class,
        DanaHistoryModule::class,
        DanaRModule::class,
        DanaRSModule::class,
        ComboModule::class,
        InsightModule::class,
        InsightDatabaseModule::class,
        WorkersModule::class,
        OHUploaderModule::class,
        DiaconnG8Module::class
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