package info.nightscout.androidaps.dana.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.dana.DanaFragment
import info.nightscout.androidaps.dana.activities.DanaHistoryActivity
import info.nightscout.androidaps.dana.activities.DanaUserOptionsActivity

@Module
@Suppress("unused")
abstract class DanaModule {

    @ContributesAndroidInjector abstract fun contributesDanaRFragment(): DanaFragment
    @ContributesAndroidInjector abstract fun contributeDanaRHistoryActivity(): DanaHistoryActivity
    @ContributesAndroidInjector abstract fun contributeDanaRUserOptionsActivity(): DanaUserOptionsActivity

}