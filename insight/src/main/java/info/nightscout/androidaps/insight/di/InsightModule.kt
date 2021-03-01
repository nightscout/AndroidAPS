package info.nightscout.androidaps.danars.di

import dagger.Module

@Module(includes = [
    InsightCommModule::class,
    InsightActivitiesModule::class,
    InsightServicesModule::class
])
open class InsightModule