package app.aaps.di

import app.aaps.core.objects.di.CoreModule
import app.aaps.core.validators.di.ValidatorsModule
import app.aaps.database.di.TestDatabaseModule
import app.aaps.database.persistence.di.PersistenceModule
import app.aaps.implementation.di.ImplementationModule
import app.aaps.plugins.aps.di.ApsModule
import app.aaps.plugins.automation.di.AutomationModule
import app.aaps.plugins.configuration.di.ConfigurationModule
import app.aaps.plugins.constraints.di.PluginsConstraintsModule
import app.aaps.plugins.main.di.PluginsModule
import app.aaps.plugins.source.di.SourceModule
import app.aaps.plugins.sync.di.OpenHumansModule
import app.aaps.plugins.sync.di.SyncModule
import app.aaps.pump.virtual.di.VirtualPumpModule
import app.aaps.shared.impl.di.LoggerModule
import app.aaps.shared.impl.di.SharedImplModule
import app.aaps.ui.di.UiModule
import app.aaps.workflow.di.WorkflowModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        // Inject TestApplication
        TestModule::class,
        TestsInjectionModule::class,
        AlgModule::class,
        // Common modules
        AndroidInjectionModule::class,

        PluginsListModule::class,
        ActivitiesModule::class,
        ReceiversModule::class,
        PersistenceModule::class,

        // Gradle modules
        AutomationModule::class,
        ApsModule::class,
        ConfigurationModule::class,
        CoreModule::class,
        // -> DatabaseModule::class, replace by in-memory database
        TestDatabaseModule::class,
        ImplementationModule::class,
        OpenHumansModule::class,
        PluginsModule::class,
        SharedImplModule::class,
        LoggerModule::class,
        UiModule::class,
        ValidatorsModule::class,
        PluginsConstraintsModule::class,
        SourceModule::class,
        SyncModule::class,
        WorkflowModule::class,

        // pumps
        PumpDriversModule::class,
        VirtualPumpModule::class
    ]
)
interface TestAppComponent : AndroidInjector<TestApplication> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(mainApp: TestApplication): Builder

        fun build(): TestAppComponent
    }
}