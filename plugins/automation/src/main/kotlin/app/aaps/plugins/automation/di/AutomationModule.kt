package app.aaps.plugins.automation.di

import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.automation.BtConnectionSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * What is left of automation's own wiring: three bindings onto [AutomationRuntime].
 *
 * The `@ContributesAndroidInjector` entries that used to fill this file are gone. The triggers,
 * actions and events they served now take their dependencies through constructors, and the two
 * Android entry points that genuinely needed injecting - the location service and the reminder
 * receiver - live in :app now, because a platform entry point cannot carry a Dagger annotation
 * inside a multiplatform module.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AutomationModule {

    @Binds fun bindAutomation(automationRuntime: AutomationRuntime): Automation

    @Binds @IntoSet fun bindAutomationPermissionProvider(automationRuntime: AutomationRuntime): PermissionProvider

    @Binds fun bindBtConnectionSource(automationRuntime: AutomationRuntime): BtConnectionSource
}
