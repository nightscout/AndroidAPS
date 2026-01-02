package app.aaps.di

import app.aaps.CompatDbHelperTest
import app.aaps.LoopTest
import app.aaps.ReplayApsResultsTest
import app.aaps.RunningConfigurationTest
import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * Android injectors of test classes
 */
@Module
@Suppress("unused")
abstract class TestsInjectionModule {

    // Tests
    @ContributesAndroidInjector abstract fun contributesReplayApsResultsTest(): ReplayApsResultsTest
    @ContributesAndroidInjector abstract fun contributesLoopTest(): LoopTest
    @ContributesAndroidInjector abstract fun contributesCompatDbHelperTest(): CompatDbHelperTest
    @ContributesAndroidInjector abstract fun contributesRunningConfigurationTest(): RunningConfigurationTest
}