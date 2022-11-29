package info.nightscout.pump.dana.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.dana.DanaFragment
import info.nightscout.pump.dana.activities.DanaHistoryActivity
import info.nightscout.pump.dana.activities.DanaUserOptionsActivity

@Module
@Suppress("unused")
abstract class DanaModule {

    @ContributesAndroidInjector abstract fun contributesDanaRFragment(): DanaFragment
    @ContributesAndroidInjector abstract fun contributeDanaRHistoryActivity(): DanaHistoryActivity
    @ContributesAndroidInjector abstract fun contributeDanaRUserOptionsActivity(): DanaUserOptionsActivity

}