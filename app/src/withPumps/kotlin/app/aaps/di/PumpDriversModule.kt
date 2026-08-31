package app.aaps.di


import app.aaps.di.pump.MedtrumModule
import app.aaps.di.pump.OmnipodDashModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        OmnipodDashModule::class,
        MedtrumModule::class,
        EquilModules::class,
    ]
)
@InstallIn(SingletonComponent::class)
abstract class PumpDriversModule
