package info.nightscout.androidaps.plugins.pump.omnipod.common.dagger

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.PodDeactivationWizardActivity

@Module
@Suppress("unused")
abstract class OmnipodCommonModule {
    // ACTIVITIES

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class])
    abstract fun contributesActivationWizardActivity(): PodActivationWizardActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class])
    abstract fun contributesDeactivationWizardActivity(): PodDeactivationWizardActivity
}