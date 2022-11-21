package info.nightscout.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.dialogs.ErrorDialog
import info.nightscout.androidaps.plugins.general.maintenance.activities.PrefImportListActivity
import info.nightscout.core.ui.elements.SingleClickButton

@Module
@Suppress("unused")
abstract class CoreFragmentsModule {

    @ContributesAndroidInjector abstract fun contributesPrefImportListActivity(): PrefImportListActivity
    @ContributesAndroidInjector abstract fun contributesBolusProgressDialog(): BolusProgressDialog
    @ContributesAndroidInjector abstract fun contributesErrorDialog(): ErrorDialog
    @ContributesAndroidInjector abstract fun contributesSingleClickButton(): SingleClickButton

}
