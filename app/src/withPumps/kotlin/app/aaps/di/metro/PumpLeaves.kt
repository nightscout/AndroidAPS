package app.aaps.di.metro

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.danars.comm.DanaRSPacket
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import javax.inject.Provider

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
    private val danaHistoryDatabaseProvider: Provider<DanaHistoryDatabase>,
    private val danaRSPacketsProvider: Provider<Set<DanaRSPacket>>
) {

    @Provides fun bleTransport(): BleTransport = bleTransportProvider.get()

    @Provides fun danaHistoryDatabase(): DanaHistoryDatabase = danaHistoryDatabaseProvider.get()

    /** The command set, already assembled by Dagger's @IntoSet multibinding in `DanaRSModule`. */
    @Provides fun danaRSPackets(): Set<DanaRSPacket> = danaRSPacketsProvider.get()
}
