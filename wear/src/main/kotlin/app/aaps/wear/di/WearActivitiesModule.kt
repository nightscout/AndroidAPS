package app.aaps.wear.di

import app.aaps.wear.complications.ComplicationTapActivity
import app.aaps.wear.interaction.ConfigurationActivity
import app.aaps.wear.interaction.TileConfigurationActivity
import app.aaps.wear.interaction.WatchfaceConfigurationActivity
import app.aaps.wear.interaction.actions.AcceptActivity
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.BolusActivity
import app.aaps.wear.interaction.actions.CarbActivity
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.FillActivity
import app.aaps.wear.interaction.actions.LoopStateTimedActivity
import app.aaps.wear.interaction.actions.ProfileSwitchActivity
import app.aaps.wear.interaction.actions.QuickSnoozeActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.ViewSelectorActivity
import app.aaps.wear.interaction.actions.WizardActivity
import app.aaps.wear.interaction.actions.WizardConfirmFragment
import app.aaps.wear.interaction.actions.WizardResultActivity
import app.aaps.wear.interaction.actions.WizardResultFragment
import app.aaps.wear.interaction.activities.LoopStatusActivity
import app.aaps.wear.interaction.menus.FillMenuActivity
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.interaction.menus.PreferenceMenuActivity
import app.aaps.wear.interaction.menus.StatusMenuActivity
import app.aaps.wear.interaction.utils.MenuListActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class WearActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesTileConfigurationActivity(): TileConfigurationActivity
    @ContributesAndroidInjector abstract fun contributesConfigurationActivity(): ConfigurationActivity
    @ContributesAndroidInjector abstract fun contributesWatchfaceConfigurationActivity(): WatchfaceConfigurationActivity
    @ContributesAndroidInjector abstract fun contributesComplicationTapActivity(): ComplicationTapActivity

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
    @ContributesAndroidInjector abstract fun wizardResultActivity(): WizardResultActivity
    @ContributesAndroidInjector abstract fun contributesWizardResultFragment(): WizardResultFragment
    @ContributesAndroidInjector abstract fun contributesWizardConfirmFragment(): WizardConfirmFragment

    @ContributesAndroidInjector abstract fun contributesMenuListActivity(): MenuListActivity
    @ContributesAndroidInjector abstract fun contributesFillMenuActivity(): FillMenuActivity
    @ContributesAndroidInjector abstract fun contributesPreferenceMenuActivity(): PreferenceMenuActivity
    @ContributesAndroidInjector abstract fun contributesMainMenuActivity(): MainMenuActivity
    @ContributesAndroidInjector abstract fun contributesStatusMenuActivity(): StatusMenuActivity
    @ContributesAndroidInjector abstract fun contributesQuickSnoozeActivity(): QuickSnoozeActivity
    @ContributesAndroidInjector abstract fun contributesLoopStateTimedActivity(): LoopStateTimedActivity
    @ContributesAndroidInjector abstract fun contributesLoopStatusActivity(): LoopStatusActivity
}
