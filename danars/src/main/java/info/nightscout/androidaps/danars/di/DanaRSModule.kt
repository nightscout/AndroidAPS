package info.nightscout.androidaps.danars.di

import dagger.Module

@Module(includes = [
    DanaRSCommModule::class,
    DanaRSActivitiesModule::class,
    DanaRSServicesModule::class
])
open class DanaRSModule