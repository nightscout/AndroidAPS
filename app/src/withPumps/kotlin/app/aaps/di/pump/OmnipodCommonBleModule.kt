package app.aaps.di.pump

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.LegacyBleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.LegacyBleDeviceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Omnipod BLE bindings, provided from `:app` so `:pump:omnipod:common` needs no Dagger processor.
 *
 * Scope on the implementations, not on the `@Binds`: Metro reads this module and rejects a scoped
 * `@Binds`. Both impls carry `@Singleton` themselves, so the instance is still single - the same shape
 * `AppModule.bindHistoryScope` already uses.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OmnipodCommonBleModule {

    @Binds
    abstract fun bindBleConnectionFactory(impl: LegacyBleConnectionFactory): BleConnectionFactory

    @Binds
    abstract fun bindBleDeviceManager(impl: LegacyBleDeviceManager): BleDeviceManager
}
