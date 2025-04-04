package app.aaps.plugins.automationstate.di

import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.plugins.automationstate.AutomationStatePlugin
import app.aaps.plugins.automationstate.dialogs.AutomationAddStateDialog
import app.aaps.plugins.automationstate.dialogs.AutomationStateValuesDialog
import app.aaps.plugins.automationstate.services.AutomationStateService
import app.aaps.plugins.automationstate.ui.AutomationStateFragment
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import javax.inject.Singleton

@Module
abstract class AutomationStateModule {

    @ContributesAndroidInjector 
    abstract fun contributesAutomationStateFragment(): AutomationStateFragment
    
    @ContributesAndroidInjector 
    abstract fun contributesAutomationAddStateDialog(): AutomationAddStateDialog
    
    @ContributesAndroidInjector 
    abstract fun contributesAutomationStateValuesDialog(): AutomationStateValuesDialog

    @Binds
    abstract fun bindAutomationStatePlugin(automationStatePlugin: AutomationStatePlugin): app.aaps.core.interfaces.plugin.PluginBase
    
    companion object {
        @Provides
        @Singleton
        fun provideAutomationStateInterface(automationStateService: AutomationStateService): AutomationStateInterface {
            return automationStateService
        }
    }
} 