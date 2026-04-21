package info.nightscout.androidaps.plugins.pump.carelevo.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.carelevo.CarelevoPumpPlugin

@Module(includes = [
    CarelevoBleModule::class,
    CarelevoProtocolParserModule::class,
    CarelevoDataSourceModule::class,
    CarelevoDaoModule::class,
    CarelevoManagerModule::class,
    CarelevoRepositoryModule::class,
    CarelevoUseCaseModule::class
])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class CarelevoModule {

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1190)
    abstract fun bindCarelevoPumpPlugin(plugin: CarelevoPumpPlugin): PluginBase
}
