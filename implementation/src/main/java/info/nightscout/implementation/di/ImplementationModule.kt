package info.nightscout.implementation.di

import android.content.Context
import dagger.Module
import dagger.Provides
import info.nightscout.core.fabric.FabricPrivacy
import info.nightscout.shared.interfaces.ResourceHelper
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
        info.nightscout.implementation.resources.ResourceHelperImpl(context, fabricPrivacy)
}