package app.aaps.di

import app.aaps.T2CompatDbHelperTest
import app.aaps.T1LoopTest
import app.aaps.T3ReplayApsResultsTest
import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * Android injectors of test classes
 */
@Module
@Suppress("unused")
abstract class TestsInjectionModule {

    // Tests
    @ContributesAndroidInjector abstract fun contributesReplayApsResultsTest(): T3ReplayApsResultsTest
    @ContributesAndroidInjector abstract fun contributesLoopTest(): T1LoopTest
    @ContributesAndroidInjector abstract fun contributesCompatDbHelperTest(): T2CompatDbHelperTest
}