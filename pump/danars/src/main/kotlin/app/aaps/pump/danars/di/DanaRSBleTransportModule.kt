package app.aaps.pump.danars.di

import app.aaps.pump.danars.services.BleTransport
import app.aaps.pump.danars.services.BleTransportImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DanaRSBleTransportModule {

    @Binds
    abstract fun bindBleTransport(impl: BleTransportImpl): BleTransport
}
