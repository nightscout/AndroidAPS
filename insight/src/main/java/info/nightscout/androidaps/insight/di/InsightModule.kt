package info.nightscout.androidaps.insight.di

import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(includes = [
    InsightCommModule::class,
    InsightActivitiesModule::class,
    InsightServicesModule::class,
    InsightDatabaseModule::class
])

@Suppress("unused")
abstract class InsightModule