package app.aaps.pump.medtrum.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.services.MedtrumService
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(includes = [MedtrumCommModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MedtrumModule {

    // SERVICE
    @ContributesAndroidInjector
    abstract fun contributesMedtrumService(): MedtrumService

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1120)
    abstract fun bindMedtrumPlugin(plugin: MedtrumPlugin): PluginBase
}
