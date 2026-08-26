package app.aaps.pump.medtronic.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtronic.service.RileyLinkMedtronicService
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MedtronicModule {

    // Still dagger.android: member injecting it trips a Metro codegen bug - the fields it inherits from
    // RileyLinkService reach graph built objects. See https://github.com/ZacSweers/metro/issues/2731.
    @ContributesAndroidInjector
    abstract fun contributesRileyLinkMedtronicService(): RileyLinkMedtronicService

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1090)
    abstract fun bindMedtronicPumpPlugin(plugin: MedtronicPumpPlugin): PluginBase
}