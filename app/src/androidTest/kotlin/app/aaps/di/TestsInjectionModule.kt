package app.aaps.di

import app.aaps.plugins.aps.ReplayApsResultsTest
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class TestsInjectionModule {

    @ContributesAndroidInjector abstract fun contributesReplayApsResultsTest(): ReplayApsResultsTest
}