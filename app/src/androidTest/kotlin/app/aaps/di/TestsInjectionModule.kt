package app.aaps.di

import app.aaps.database.persistence.CompatDbHelperTest
import app.aaps.helpers.RxHelper
import app.aaps.plugins.aps.LoopTest
import app.aaps.plugins.aps.ReplayApsResultsTest
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
}