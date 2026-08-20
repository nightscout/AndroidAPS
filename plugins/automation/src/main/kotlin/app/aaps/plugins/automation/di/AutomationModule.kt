package app.aaps.plugins.automation.di

import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.automation.BtConnectionSource
import app.aaps.plugins.automation.TimerReminderReceiver
import app.aaps.plugins.automation.services.LocationService
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module(
    includes = [
        AutomationModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class AutomationModule {

    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService
    @ContributesAndroidInjector abstract fun contributesTimerReminderReceiver(): TimerReminderReceiver

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindAutomation(automationRuntime: AutomationRuntime): Automation

        @Binds @IntoSet fun bindAutomationPermissionProvider(automationRuntime: AutomationRuntime): PermissionProvider

        @Binds fun bindBtConnectionSource(automationRuntime: AutomationRuntime): BtConnectionSource
    }
}