package app.aaps.di.metro

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.danars.comm.DanaRSPacket
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Builds [PumpLeaves] for a build that has pump drivers.
 *
 * Constructed by hand rather than injected: a binding container with an `@Inject` constructor crashes
 * the Metro compiler under Dagger interop, the same reason `AapsLeaves` is built this way.
 */
@Module
@InstallIn(SingletonComponent::class)
class PumpLeavesModule {

    @Provides
    @Singleton
    fun providePumpLeaves(
        bleTransport: Provider<BleTransport>,
        danaHistoryDatabase: Provider<DanaHistoryDatabase>,
        danaRSPackets: Provider<Set<DanaRSPacket>>
    ): PumpLeaves = PumpLeaves(bleTransport, danaHistoryDatabase, danaRSPackets)
}
