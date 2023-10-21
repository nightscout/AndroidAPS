package com.microtechmd.equil.di

import dagger.Module

@Module(
    includes = [
        EquilActivitiesModule::class,
        EquilServicesModule::class,
        EquilHistoryModule::class
    ]
)

open class EquilModule