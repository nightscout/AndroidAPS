package app.aaps.pump.apex.di

import dagger.Module

@Module(includes = [
    ApexUiModule::class,
    ApexServicesModule::class,
])
open class ApexModule
