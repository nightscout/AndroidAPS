package app.aaps.pump.medtrum.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.ble.MedtrumBleTransport
import app.aaps.pump.medtrum.ble.MedtrumBleTransportImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MedtrumModule {

    // Scope on the implementation, not the binding: Metro reads this module now that interop is on for
    // the module, and it rejects a scoped @Binds. The class carries @Singleton itself.
    @Binds
    abstract fun bindMedtrumBleTransport(impl: MedtrumBleTransportImpl): MedtrumBleTransport

}
