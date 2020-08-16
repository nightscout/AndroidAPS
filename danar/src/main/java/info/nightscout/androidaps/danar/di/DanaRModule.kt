package info.nightscout.androidaps.danar.di

import dagger.Module

@Module(includes = [
    DanaRCommModule::class,
    DanaRServicesModule::class
])
open class DanaRModule