package app.aaps.pump.danar.di

import dagger.Module

@Module(
    includes = [
        DanaRCommModule::class,
        DanaRServicesModule::class
    ]
)
open class DanaRModule