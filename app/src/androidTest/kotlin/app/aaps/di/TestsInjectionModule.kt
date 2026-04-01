package app.aaps.di

import app.aaps.CobExtendedCarbsTest
import app.aaps.LoopTest
import app.aaps.ReplayApsResultsTest
import app.aaps.RunningConfigurationTest
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.migration.DisableInstallInCheck

// Used in DaggerTestAppComponent (plain Dagger), not Hilt.
@Module
@DisableInstallInCheck
@Suppress("unused")
abstract class TestsInjectionModule {

    // Tests
    @ContributesAndroidInjector abstract fun contributesReplayApsResultsTest(): ReplayApsResultsTest
    @ContributesAndroidInjector abstract fun contributesLoopTest(): LoopTest
    @ContributesAndroidInjector abstract fun contributesRunningConfigurationTest(): RunningConfigurationTest
    @ContributesAndroidInjector abstract fun contributesCobExtendedCarbsTest(): CobExtendedCarbsTest
}