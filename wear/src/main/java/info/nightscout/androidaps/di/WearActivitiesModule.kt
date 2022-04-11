package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.interaction.actions.*

@Module
@Suppress("unused")
abstract class WearActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesBackgroundActionActivity(): BackgroundActionActivity

    @ContributesAndroidInjector abstract fun contributesViewSelectorActivity(): ViewSelectorActivity
    @ContributesAndroidInjector abstract fun contributesAcceptActivity(): AcceptActivity
    @ContributesAndroidInjector abstract fun contributesBolusActivity(): BolusActivity
    @ContributesAndroidInjector abstract fun contributesCarbActivity(): CarbActivity
    @ContributesAndroidInjector abstract fun contributesCPPActivity(): CPPActivity
    @ContributesAndroidInjector abstract fun contributesECarbActivity(): ECarbActivity
    @ContributesAndroidInjector abstract fun contributesFillActivity(): FillActivity
    @ContributesAndroidInjector abstract fun contributesTempTargetActivity(): TempTargetActivity
    @ContributesAndroidInjector abstract fun contributesTreatmentActivity(): TreatmentActivity
    @ContributesAndroidInjector abstract fun contributesWizardActivity(): WizardActivity
}