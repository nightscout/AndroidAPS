package app.aaps.di

import app.aaps.MainApp
import app.aaps.core.main.di.CoreModule
import app.aaps.core.validators.di.ValidatorsModule
import app.aaps.implementation.di.ImplementationModule
import app.aaps.plugins.aps.di.ApsModule
import app.aaps.plugins.automation.di.AutomationModule
import app.aaps.plugins.configuration.di.ConfigurationModule
import app.aaps.plugins.constraints.di.PluginsConstraintsModule
import app.aaps.plugins.insulin.di.InsulinModule
import app.aaps.shared.impl.di.SharedImplModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.danar.di.DanaRModule
import info.nightscout.androidaps.insight.di.InsightDatabaseModule
import info.nightscout.androidaps.insight.di.InsightModule
import info.nightscout.androidaps.plugins.pump.common.di.RileyLinkModule
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchModule
import info.nightscout.androidaps.plugins.pump.medtronic.di.MedtronicModule
import info.nightscout.androidaps.plugins.pump.omnipod.dash.di.OmnipodDashModule
import info.nightscout.androidaps.plugins.pump.omnipod.eros.di.OmnipodErosModule
import info.nightscout.database.impl.DatabaseModule
import info.nightscout.plugins.di.PluginsModule
import info.nightscout.plugins.sync.di.OpenHumansModule
import info.nightscout.plugins.sync.di.SyncModule
import info.nightscout.pump.combo.di.ComboModule
import info.nightscout.pump.combov2.di.ComboV2Module
import info.nightscout.pump.common.di.PumpCommonModule
import info.nightscout.pump.dana.di.DanaHistoryModule
import info.nightscout.pump.dana.di.DanaModule
import info.nightscout.pump.danars.di.DanaRSModule
import info.nightscout.pump.diaconn.di.DiaconnG8Module
import info.nightscout.pump.medtrum.di.MedtrumModule
import info.nightscout.pump.virtual.di.VirtualPumpModule
import info.nightscout.source.di.SourceModule
import info.nightscout.ui.di.UiModule
import info.nightscout.workflow.di.WorkflowModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        AppModule::class,
        PluginsListModule::class,
        ActivitiesModule::class,
        ReceiversModule::class,

        // Gradle modules
        AutomationModule::class,
        ApsModule::class,
        ConfigurationModule::class,
        CoreModule::class,
        DatabaseModule::class,
        ImplementationModule::class,
        InsulinModule::class,
        OpenHumansModule::class,
        PluginsModule::class,
        SharedImplModule::class,
        UiModule::class,
        ValidatorsModule::class,
        PluginsConstraintsModule::class,
        SourceModule::class,
        SyncModule::class,
        WorkflowModule::class,

        // pumps
        ComboModule::class,
        ComboV2Module::class,
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
        MedtrumModule::class,
        VirtualPumpModule::class
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