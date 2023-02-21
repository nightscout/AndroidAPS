package info.nightscout.pump.medtrum.di

import dagger.Module

@Module(includes = [
    MedtrumActivitiesModule::class,
    MedtrumServicesModule::class
])
open class MedtrumModule 