package info.nightscout.androidaps.di

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.combo.di.ComboModule
import info.nightscout.androidaps.dana.di.DanaHistoryModule
import info.nightscout.androidaps.dana.di.DanaModule
import info.nightscout.androidaps.danar.di.DanaRModule
import info.nightscout.androidaps.danars.di.DanaRSModule
import info.nightscout.androidaps.database.DatabaseModule
import info.nightscout.androidaps.diaconn.di.DiaconnG8Module
import info.nightscout.androidaps.insight.di.InsightDatabaseModule
import info.nightscout.androidaps.insight.di.InsightModule
import info.nightscout.androidaps.plugin.general.openhumans.di.OpenHumansModule
import info.nightscout.androidaps.plugins.pump.common.di.PumpCommonModule
import info.nightscout.androidaps.plugins.pump.common.di.RileyLinkModule
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchModule
import info.nightscout.androidaps.plugins.pump.medtronic.di.MedtronicModule
import info.nightscout.androidaps.plugins.pump.omnipod.dash.di.OmnipodDashModule
import info.nightscout.androidaps.plugins.pump.omnipod.eros.di.OmnipodErosModule
import info.nightscout.automation.di.AutomationModule
import info.nightscout.implementation.di.ImplementationModule
import info.nightscout.plugins.di.PluginsModule
import info.nightscout.shared.di.SharedModule
import info.nightscout.ui.di.UiModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        AppModule::class,
        PluginsListModule::class,
        SkinsModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        ReceiversModule::class,
        ServicesModule::class,
        ObjectivesModule::class,
        WizardModule::class,
        APSModule::class,
        WorkflowModule::class,
        PreferencesModule::class,
        OverviewModule::class,
        DataClassesModule::class,
        WorkersModule::class,
        UiModule::class,

        // Gradle modules
        AutomationModule::class,
        CoreModule::class,
        DatabaseModule::class,
        ImplementationModule::class,
        PluginsModule::class,
        SharedModule::class,
        OpenHumansModule::class,
        UIModule::class,

        // pumps
        ComboModule::class,
        DanaHistoryModule::class,
        DanaModule::class,
        DanaRModule::class,
        DanaRSModule::class,
        DiaconnG8Module::class,
        EopatchModule::class,
        InsightModule::class,
        InsightDatabaseModule::class,
        MedtronicModule::class,
        OmnipodDashModule::class,
        OmnipodErosModule::class,
        PumpCommonModule::class,
        RileyLinkModule::class,

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