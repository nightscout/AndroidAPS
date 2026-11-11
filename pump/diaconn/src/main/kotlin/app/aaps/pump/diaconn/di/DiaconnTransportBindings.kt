package app.aaps.pump.diaconn.di

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.pump.diaconn.service.DiaconnBleTransportImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** The Bluetooth link of the Diaconn G8. Dana RS binds the plain `BleTransport` already. */
@Qualifier
annotation class DiaconnBle

/** Picks the Diaconn G8 transport. */
@ContributesTo(AppScope::class)
@BindingContainer
object DiaconnTransportBindings {

    @Provides
    @SingleIn(AppScope::class)
    @DiaconnBle
    fun provideBleTransport(diaconnBleTransportImpl: DiaconnBleTransportImpl): BleTransport = diaconnBleTransportImpl
}
