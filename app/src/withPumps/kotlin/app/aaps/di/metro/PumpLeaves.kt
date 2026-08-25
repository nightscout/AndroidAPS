package app.aaps.di.metro

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.danars.comm.DanaRSPacket
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import javax.inject.Provider
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
 * Pump-only objects Dagger still owns, offered to the root graph as one container.
 *
 * There is a copy of this class in `src/aapsclient` with the same name and no bindings. Only one is
 * compiled into any build, so `AppRootGraph` can name it from `src/main` while the contents differ per
 * flavour - the same trick `PumpDriversModule` already uses.
 *
 * That split is the whole reason this file exists. [BleTransport] is chosen at runtime by
 * `DanaModules.provideBleTransport`, which swaps in `EmulatorBleTransport` when an emulator option is
 * on. It therefore cannot be contributed by either implementation, and it cannot go in `AapsLeaves`
 * either: that class lives in `src/main` and is compiled for the follower builds too, where no pump
 * module is on the classpath at all.
 *
 * Deliberately no `@Inject` constructor. A binding container that Metro can also construct crashes the
 * compiler when Dagger interop is on - see the note in `CoreObjectsModule` and
 * https://github.com/ZacSweers/metro/issues/2727.
 */
@BindingContainer
class PumpLeaves(
    private val bleTransportProvider: Provider<BleTransport>,
    // Same emulator seam as BleTransport: DanaModules picks EmulatorRfcommTransport by config.
    private val rfcommTransportProvider: Provider<RfcommTransport>,
    private val danaHistoryRecordDaoProvider: Provider<DanaHistoryRecordDao>,
    private val diaconnHistoryRecordDaoProvider: Provider<DiaconnHistoryRecordDao>,
    private val equilBleTransportProvider: Provider<EquilBleTransport>,
    private val equilHistoryPumpDaoProvider: Provider<EquilHistoryPumpDao>,
    private val equilHistoryRecordDaoProvider: Provider<EquilHistoryRecordDao>,
    private val dashHistoryDatabaseProvider: Provider<DashHistoryDatabase>,
    private val historyMapperProvider: Provider<HistoryMapper>,
    private val historyRecordDaoProvider: Provider<HistoryRecordDao>,
    private val medtrumBleTransportProvider: Provider<MedtrumBleTransport>,
    private val omnipodDashManagerProvider: Provider<OmnipodDashManager>,
    private val omnipodDashPodStateManagerProvider: Provider<OmnipodDashPodStateManager>,
    private val danaHistoryDatabaseProvider: Provider<DanaHistoryDatabase>,
    private val danaRSPacketsProvider: Provider<Set<DanaRSPacket>>
) {

    @Provides fun bleTransport(): BleTransport = bleTransportProvider.get()
    @Provides fun rfcommTransport(): RfcommTransport = rfcommTransportProvider.get()
    @Provides fun danaHistoryRecordDao(): DanaHistoryRecordDao = danaHistoryRecordDaoProvider.get()
    @Provides fun diaconnHistoryRecordDao(): DiaconnHistoryRecordDao = diaconnHistoryRecordDaoProvider.get()
    @Provides fun equilBleTransport(): EquilBleTransport = equilBleTransportProvider.get()
    @Provides fun equilHistoryPumpDao(): EquilHistoryPumpDao = equilHistoryPumpDaoProvider.get()
    @Provides fun equilHistoryRecordDao(): EquilHistoryRecordDao = equilHistoryRecordDaoProvider.get()
    @Provides fun dashHistoryDatabase(): DashHistoryDatabase = dashHistoryDatabaseProvider.get()
    @Provides fun historyMapper(): HistoryMapper = historyMapperProvider.get()
    @Provides fun historyRecordDao(): HistoryRecordDao = historyRecordDaoProvider.get()
    @Provides fun medtrumBleTransport(): MedtrumBleTransport = medtrumBleTransportProvider.get()
    @Provides fun omnipodDashManager(): OmnipodDashManager = omnipodDashManagerProvider.get()
    @Provides fun omnipodDashPodStateManager(): OmnipodDashPodStateManager = omnipodDashPodStateManagerProvider.get()


    @Provides fun danaHistoryDatabase(): DanaHistoryDatabase = danaHistoryDatabaseProvider.get()

    /** The command set, already assembled by Dagger's @IntoSet multibinding in `DanaRSModule`. */
    @Provides fun danaRSPackets(): Set<DanaRSPacket> = danaRSPacketsProvider.get()
}
