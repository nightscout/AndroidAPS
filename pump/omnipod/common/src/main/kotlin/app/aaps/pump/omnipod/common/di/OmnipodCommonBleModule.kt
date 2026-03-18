package app.aaps.pump.omnipod.common.di

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.LegacyBleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.LegacyBleDeviceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OmnipodCommonBleModule {

    @Binds
    @Singleton
    abstract fun bindBleConnectionFactory(impl: LegacyBleConnectionFactory): BleConnectionFactory

    @Binds
    @Singleton
    abstract fun bindBleDeviceManager(impl: LegacyBleDeviceManager): BleDeviceManager
}
