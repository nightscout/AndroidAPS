package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.interaction.actions.QuickSnoozeActivity
import info.nightscout.androidaps.interaction.ConfigurationActivity
import info.nightscout.androidaps.interaction.TileConfigurationActivity
import info.nightscout.androidaps.interaction.actions.*
import info.nightscout.androidaps.interaction.menus.FillMenuActivity
import info.nightscout.androidaps.interaction.menus.MainMenuActivity
import info.nightscout.androidaps.interaction.menus.PreferenceMenuActivity
import info.nightscout.androidaps.interaction.menus.StatusMenuActivity
import info.nightscout.androidaps.interaction.utils.MenuListActivity

@Module
@Suppress("unused")
abstract class WearActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesTileConfigurationActivity(): TileConfigurationActivity
    @ContributesAndroidInjector abstract fun contributesConfigurationActivity(): ConfigurationActivity

    @ContributesAndroidInjector abstract fun contributesBackgroundActionActivity(): BackgroundActionActivity

    @ContributesAndroidInjector abstract fun contributesViewSelectorActivity(): ViewSelectorActivity
    @ContributesAndroidInjector abstract fun contributesAcceptActivity(): AcceptActivity
    @ContributesAndroidInjector abstract fun contributesBolusActivity(): BolusActivity
    @ContributesAndroidInjector abstract fun contributesCarbActivity(): CarbActivity
    @ContributesAndroidInjector abstract fun contributesProfileSwitchActivity(): ProfileSwitchActivity
    @ContributesAndroidInjector abstract fun contributesECarbActivity(): ECarbActivity
    @ContributesAndroidInjector abstract fun contributesFillActivity(): FillActivity
    @ContributesAndroidInjector abstract fun contributesTempTargetActivity(): TempTargetActivity
    @ContributesAndroidInjector abstract fun contributesTreatmentActivity(): TreatmentActivity
    @ContributesAndroidInjector abstract fun contributesWizardActivity(): WizardActivity

    @ContributesAndroidInjector abstract fun contributesMenuListActivity(): MenuListActivity
    @ContributesAndroidInjector abstract fun contributesFillMenuActivity(): FillMenuActivity
    @ContributesAndroidInjector abstract fun contributesPreferenceMenuActivity(): PreferenceMenuActivity
    @ContributesAndroidInjector abstract fun contributesMainMenuActivity(): MainMenuActivity
    @ContributesAndroidInjector abstract fun contributesStatusMenuActivity(): StatusMenuActivity
    @ContributesAndroidInjector abstract fun contributesQuickSnoozeActivity(): QuickSnoozeActivity
}
