package app.aaps.di.metro

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.danars.comm.DanaRSPacket
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import app.aaps.pump.omnipod.dash.history.mapper.HistoryMapper
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordDao
import app.aaps.pump.medtrum.ble.MedtrumBleTransport
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager

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
        rfcommTransport: Provider<RfcommTransport>,
        danaHistoryRecordDao: Provider<DanaHistoryRecordDao>,
        diaconnHistoryRecordDao: Provider<DiaconnHistoryRecordDao>,
        equilBleTransport: Provider<EquilBleTransport>,
        equilHistoryPumpDao: Provider<EquilHistoryPumpDao>,
        equilHistoryRecordDao: Provider<EquilHistoryRecordDao>,
        dashHistoryDatabase: Provider<DashHistoryDatabase>,
        historyMapper: Provider<HistoryMapper>,
        historyRecordDao: Provider<HistoryRecordDao>,
        medtrumBleTransport: Provider<MedtrumBleTransport>,
        omnipodDashManager: Provider<OmnipodDashManager>,
        omnipodDashPodStateManager: Provider<OmnipodDashPodStateManager>,
        danaHistoryDatabase: Provider<DanaHistoryDatabase>,
        danaRSPackets: Provider<Set<DanaRSPacket>>
    ): PumpLeaves = PumpLeaves(bleTransport, rfcommTransport, danaHistoryRecordDao, diaconnHistoryRecordDao, equilBleTransport, equilHistoryPumpDao, equilHistoryRecordDao, dashHistoryDatabase, historyMapper, historyRecordDao, medtrumBleTransport, omnipodDashManager, omnipodDashPodStateManager, danaHistoryDatabase, danaRSPackets)
}
