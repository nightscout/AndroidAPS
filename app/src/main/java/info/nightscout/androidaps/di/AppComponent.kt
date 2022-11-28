package info.nightscout.androidaps.di

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.danar.di.DanaRModule
import info.nightscout.androidaps.insight.di.InsightDatabaseModule
import info.nightscout.androidaps.insight.di.InsightModule
import info.nightscout.androidaps.plugin.general.openhumans.di.OpenHumansModule
import info.nightscout.androidaps.plugins.pump.common.di.RileyLinkModule
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchModule
import info.nightscout.androidaps.plugins.pump.medtronic.di.MedtronicModule
import info.nightscout.androidaps.plugins.pump.omnipod.dash.di.OmnipodDashModule
import info.nightscout.androidaps.plugins.pump.omnipod.eros.di.OmnipodErosModule
import info.nightscout.automation.di.AutomationModule
import info.nightscout.configuration.di.ConfigurationModule
import info.nightscout.core.di.CoreModule
import info.nightscout.core.validators.di.ValidatorsModule
import info.nightscout.database.impl.DatabaseModule
import info.nightscout.implementation.di.ImplementationModule
import info.nightscout.plugins.aps.di.ApsModule
import info.nightscout.plugins.di.PluginsModule
import info.nightscout.plugins.support.di.PluginsSupportModule
import info.nightscout.pump.combo.di.ComboModule
import info.nightscout.pump.combov2.di.ComboV2Module
import info.nightscout.pump.common.di.PumpCommonModule
import info.nightscout.pump.dana.di.DanaHistoryModule
import info.nightscout.pump.dana.di.DanaModule
import info.nightscout.pump.danars.di.DanaRSModule
import info.nightscout.pump.diaconn.di.DiaconnG8Module
import info.nightscout.rx.di.RxModule
import info.nightscout.shared.di.SharedModule
import info.nightscout.shared.impl.di.SharedImplModule
import info.nightscout.ui.di.UiModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        AppModule::class,
        PluginsListModule::class,
        ActivitiesModule::class,
        FragmentsModule::class,
        ReceiversModule::class,
        ServicesModule::class,
        WorkflowModule::class,

        // Gradle modules
        AutomationModule::class,
        AutomationModule.Bindings::class,
        ApsModule::class,
        ConfigurationModule::class,
        ConfigurationModule.Bindings::class,
        CoreModule::class,
        DatabaseModule::class,
        ImplementationModule::class,
        ImplementationModule.Bindings::class,
        OpenHumansModule::class,
        PluginsModule::class,
        PluginsModule.Bindings::class,
        RxModule::class,
        SharedModule::class,
        SharedImplModule::class,
        UiModule::class,
        ValidatorsModule::class,
        PluginsSupportModule::class,
        PluginsSupportModule.Bindings::class,

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
        RileyLinkModule::class

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