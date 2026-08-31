package app.aaps.di.pump

import app.aaps.pump.medtrum.ble.MedtrumBleTransport
import app.aaps.pump.medtrum.ble.MedtrumBleTransportImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provided from `:app` so `:pump:medtrum` needs no Dagger processor.
 *
 * No scope on the `@Binds`: Metro rejects a scoped one, and `MedtrumBleTransportImpl` carries
 * `@Singleton` itself, so there is still exactly one.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MedtrumModule {

    @Binds
    abstract fun bindMedtrumBleTransport(impl: MedtrumBleTransportImpl): MedtrumBleTransport
}
