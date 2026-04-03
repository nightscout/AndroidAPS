package app.aaps.ui.di

import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.activities.ProfileViewerActivity
import app.aaps.ui.activities.QuickWizardListActivity
import app.aaps.ui.activities.TDDStatsActivity
import app.aaps.ui.compose.overview.OverviewDataCacheImpl
import app.aaps.ui.dialogs.CalibrationDialog
import app.aaps.ui.dialogs.CarbsDialog
import app.aaps.ui.dialogs.CareDialog
import app.aaps.ui.dialogs.EditQuickWizardDialog
import app.aaps.ui.dialogs.ExtendedBolusDialog
import app.aaps.ui.dialogs.FillDialog
import app.aaps.ui.dialogs.InsulinDialog
import app.aaps.ui.dialogs.LoopDialog
import app.aaps.ui.dialogs.ProfileSwitchDialog
import app.aaps.ui.dialogs.SiteRotationDialog
import app.aaps.ui.dialogs.TempBasalDialog
import app.aaps.ui.dialogs.TempTargetDialog
import app.aaps.ui.dialogs.TreatmentDialog
import app.aaps.ui.dialogs.WizardDialog

import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.DialogSearchables
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.widget.Widget
import app.aaps.ui.widget.WidgetConfigureActivity
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module(includes = [UiModule.Bindings::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class UiModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindOverviewDataCache(impl: OverviewDataCacheImpl): OverviewDataCache

        @Binds @IntoSet fun bindBuiltInSearchables(impl: BuiltInSearchables): SearchableProvider
        @Binds @IntoSet fun bindDialogSearchables(impl: DialogSearchables): SearchableProvider
    }

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService

    @ContributesAndroidInjector abstract fun contributesWidget(): Widget
    @ContributesAndroidInjector abstract fun contributesWidgetConfigureActivity(): WidgetConfigureActivity

    @ContributesAndroidInjector abstract fun contributesWizardDialog(): WizardDialog
    @ContributesAndroidInjector abstract fun contributesCalibrationDialog(): CalibrationDialog
    @ContributesAndroidInjector abstract fun contributesCarbsDialog(): CarbsDialog
    @ContributesAndroidInjector abstract fun contributesCareDialog(): CareDialog
    @ContributesAndroidInjector abstract fun contributesProfileViewerActivity(): ProfileViewerActivity
    @ContributesAndroidInjector abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog
    @ContributesAndroidInjector abstract fun contributesFillDialog(): FillDialog
    @ContributesAndroidInjector abstract fun contributesSiteRotationDialog(): SiteRotationDialog
    @ContributesAndroidInjector abstract fun contributesInsulinDialog(): InsulinDialog
    @ContributesAndroidInjector abstract fun contributesTreatmentDialog(): TreatmentDialog
    @ContributesAndroidInjector abstract fun contributesProfileSwitchDialog(): ProfileSwitchDialog
    @ContributesAndroidInjector abstract fun contributesTempBasalDialog(): TempBasalDialog
    @ContributesAndroidInjector abstract fun contributesTempTargetDialog(): TempTargetDialog
    @ContributesAndroidInjector abstract fun contributesLoopDialog(): LoopDialog
    @ContributesAndroidInjector abstract fun contributesQuickWizardListActivity(): QuickWizardListActivity
    @ContributesAndroidInjector abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog

    @ContributesAndroidInjector abstract fun contributesTDDStatsActivity(): TDDStatsActivity
    @ContributesAndroidInjector abstract fun contributeErrorActivity(): ErrorActivity
}