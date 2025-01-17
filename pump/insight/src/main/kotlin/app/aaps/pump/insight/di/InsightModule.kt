package app.aaps.pump.insight.di

import dagger.Module

@Module(
    includes = [
        InsightCommModule::class,
        InsightActivitiesModule::class,
        InsightServicesModule::class,
        InsightDatabaseModule::class
    ]
)

@Suppress("unused")
abstract class InsightModule