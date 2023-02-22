package info.nightscout.pump.common.di

import dagger.Module
import info.nightscout.aaps.pump.common.di.PumpCommonModuleAbstract
import info.nightscout.aaps.pump.common.di.PumpCommonModuleImpl

@Module(
    includes = [
        PumpCommonModuleAbstract::class,
        PumpCommonModuleImpl::class
    ]
)
open class PumpCommonModule
