package app.aaps.di

import app.aaps.pump.common.di.PumpCommonModule

import app.aaps.pump.eopatch.di.EopatchModule
import app.aaps.di.pump.EquilHistoryModule
import app.aaps.pump.medtrum.di.MedtrumModule
import app.aaps.di.pump.OmnipodDashModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        DanaModules::class,
        EopatchModule::class,
        OmnipodDashModule::class,
        PumpCommonModule::class,
        MedtrumModule::class,
        EquilHistoryModule::class,
        EquilModules::class,
    ]
)
@InstallIn(SingletonComponent::class)
abstract class PumpDriversModule
