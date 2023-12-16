package app.aaps.pump.dana.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.dana.DanaFragment
import app.aaps.pump.dana.activities.DanaHistoryActivity
import app.aaps.pump.dana.activities.DanaUserOptionsActivity

@Module
@Suppress("unused")
abstract class DanaModule {

    @ContributesAndroidInjector abstract fun contributesDanaRFragment(): DanaFragment
    @ContributesAndroidInjector abstract fun contributeDanaRHistoryActivity(): DanaHistoryActivity
    @ContributesAndroidInjector abstract fun contributeDanaRUserOptionsActivity(): DanaUserOptionsActivity

}