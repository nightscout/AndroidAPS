package info.nightscout.implementation.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.core.fabric.FabricPrivacy
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.HardLimitsImpl
import info.nightscout.implementation.resources.ResourceHelperImpl
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Singleton

@Module(
    includes = [
        CommandQueueModule::class
    ]
)

@Suppress("unused")
open class ImplementationModule {

    @Provides
    @Singleton
    fun provideResources(context: Context, fabricPrivacy: FabricPrivacy): ResourceHelper =
        ResourceHelperImpl(context, fabricPrivacy)

    @Provides
    @Singleton
    fun provideHardLimits(aapsLogger: AAPSLogger, rxBus: RxBus, sp: SP, rh: ResourceHelper, context: Context, repository: AppRepository): HardLimits =
        HardLimitsImpl(aapsLogger, rxBus, sp, rh, context, repository)
}