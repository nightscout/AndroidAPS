package app.aaps.di

import app.aaps.pump.common.di.PumpCommonModule
import app.aaps.pump.common.di.RileyLinkModule
import app.aaps.pump.diaconn.di.DiaconnG8Module
import app.aaps.pump.eopatch.di.EopatchModule
import app.aaps.pump.equil.di.EquilModule
import app.aaps.pump.insight.di.InsightModule
import app.aaps.pump.medtronic.di.MedtronicModule
import app.aaps.pump.medtrum.di.MedtrumModule
import app.aaps.pump.omnipod.dash.di.OmnipodDashModule
import app.aaps.pump.omnipod.eros.di.OmnipodErosModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.pump.combov2.di.ComboV2Module

@Module(
    includes = [
        ComboV2Module::class,
        DanaModules::class,
        DiaconnG8Module::class,
        EopatchModule::class,
        InsightModule::class,
        MedtronicModule::class,
        OmnipodDashModule::class,
        OmnipodErosModule::class,
        PumpCommonModule::class,
        RileyLinkModule::class,
        MedtrumModule::class,
        EquilModule::class,
        EquilModules::class,
    ]
)
@InstallIn(SingletonComponent::class)
abstract class PumpDriversModule
