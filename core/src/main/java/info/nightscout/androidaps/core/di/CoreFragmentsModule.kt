package info.nightscout.androidaps.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.dialogs.ErrorDialog

@Module
@Suppress("unused")
abstract class CoreFragmentsModule {

    @ContributesAndroidInjector abstract fun contributeErrorHelperActivity(): ErrorHelperActivity

    @ContributesAndroidInjector abstract fun contributesBolusProgressDialog(): BolusProgressDialog
    @ContributesAndroidInjector abstract fun contributesErrorDialog(): ErrorDialog

}
