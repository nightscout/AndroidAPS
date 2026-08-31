package app.aaps.di.metro

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.eopatch.EopatchPumpPlugin
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.util.MedtronicUtil
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.TempBasalManager
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.services.BLEComm
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.ble.MedtrumBleTransport
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.dash.history.mapper.HistoryMapper
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import info.nightscout.pump.combov2.ComboV2Plugin
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
    // Same emulator seam as BleTransport: DanaModules picks EmulatorRfcommTransport by config.
    private val equilBleTransportProvider: Provider<EquilBleTransport>,
    private val medtrumBleTransportProvider: Provider<MedtrumBleTransport>,
    private val omnipodDashManagerProvider: Provider<OmnipodDashManager>,
    private val omnipodDashPodStateManagerProvider: Provider<OmnipodDashPodStateManager>,
    // The pump state holders and driver plugins - see the note above their @Provides below.
    private val omnipodErosPumpPluginProvider: Provider<OmnipodErosPumpPlugin>,
    private val erosHistoryProvider: Provider<ErosHistory>,
    private val erosPodStateManagerProvider: Provider<ErosPodStateManager>,
    private val aapsErosPodStateManagerProvider: Provider<AapsErosPodStateManager>,
    private val aapsOmnipodErosManagerProvider: Provider<AapsOmnipodErosManager>,
    private val aapsOmnipodUtilProvider: Provider<AapsOmnipodUtil>,
    private val omnipodAlertUtilProvider: Provider<OmnipodAlertUtil>
) {

    @Provides fun equilBleTransport(): EquilBleTransport = equilBleTransportProvider.get()
    @Provides fun medtrumBleTransport(): MedtrumBleTransport = medtrumBleTransportProvider.get()
    @Provides fun omnipodDashManager(): OmnipodDashManager = omnipodDashManagerProvider.get()
    @Provides fun omnipodDashPodStateManager(): OmnipodDashPodStateManager = omnipodDashPodStateManagerProvider.get()



    /** The command set, already assembled by Dagger's @IntoSet multibinding in `DanaRSModule`. */

    /*
     * Pump state holders and driver plugins, handed over from Dagger so there is exactly ONE of each.
     *
     * These carry a javax `@Singleton` and an `@Inject` constructor, and nothing else. That is enough for
     * Dagger to build them for the pump services (`DanaRSService` is still a `DaggerService`) and for the
     * command queue - and, because `:app` runs Metro with Dagger interop, it is also enough for Metro to
     * build its OWN copy for anything in its graph, which now means every pump Compose view model.
     *
     * Two instances of a pump's state is not a slow screen, it is a screen watching an object the pump
     * never writes to: the service fills Dagger's `DanaPump` while `DanaRSOverviewViewModel` reads Metro's.
     * That is what turned CI shards A and C red - "Pump never reported initialized", "Command queue never
     * went idle" - while shard B, which drives the transport with no UI, stayed green.
     *
     * A javax scope does not cross the two frameworks, so the only fix is to name the owner. Dagger owns
     * them, because the services do; this hands its instance to Metro. `PumpLeavesTest` fails if a new
     * one is added to a view model without being listed here.
     */
    @Provides fun omnipodErosPumpPlugin(): OmnipodErosPumpPlugin = omnipodErosPumpPluginProvider.get()
    // The eros view models are Metro built now, so Metro needs these two - Dagger still provides them,
    // from `app.aaps.di.pump.OmnipodErosHistoryModule`.
    @Provides fun erosHistory(): ErosHistory = erosHistoryProvider.get()
    @Provides fun erosPodStateManager(): ErosPodStateManager = erosPodStateManagerProvider.get()
    // The eros pump state, handed over for the same reason as every other pump's: these carry javax
    // @Singleton and the eros service writes to Dagger's copy, so a Metro built second one would leave
    // the screens reading an object the pump never touches. SplitBrainTest caught exactly that here.
    @Provides fun aapsErosPodStateManager(): AapsErosPodStateManager = aapsErosPodStateManagerProvider.get()
    @Provides fun aapsOmnipodErosManager(): AapsOmnipodErosManager = aapsOmnipodErosManagerProvider.get()
    @Provides fun aapsOmnipodUtil(): AapsOmnipodUtil = aapsOmnipodUtilProvider.get()
    @Provides fun omnipodAlertUtil(): OmnipodAlertUtil = omnipodAlertUtilProvider.get()
}
