package app.aaps.pump.equil.di

import dagger.Module

@Module(
    includes = [
        EquilActivitiesModule::class,
        EquilServicesModule::class,
        EquilHistoryModule::class
    ]
)

open class EquilModule