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
    private val bleTransportProvider: Provider<BleTransport>,
    // Same emulator seam as BleTransport: DanaModules picks EmulatorRfcommTransport by config.
    private val rfcommTransportProvider: Provider<RfcommTransport>,
    private val danaHistoryRecordDaoProvider: Provider<DanaHistoryRecordDao>,
    private val diaconnHistoryRecordDaoProvider: Provider<DiaconnHistoryRecordDao>,
    private val diaconnHistoryDatabaseProvider: Provider<DiaconnHistoryDatabase>,
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
    private val danaRSPacketsProvider: Provider<Set<DanaRSPacket>>,
    // The pump state holders and driver plugins - see the note above their @Provides below.
    private val bleCommProvider: Provider<BLEComm>,
    private val comboV2PluginProvider: Provider<ComboV2Plugin>,
    private val danaPumpProvider: Provider<DanaPump>,
    private val danaRSPluginProvider: Provider<DanaRSPlugin>,
    private val diaconnG8PumpProvider: Provider<DiaconnG8Pump>,
    private val equilManagerProvider: Provider<EquilManager>,
    private val equilPumpPluginProvider: Provider<EquilPumpPlugin>,
    private val medtrumPluginProvider: Provider<MedtrumPlugin>,
    private val medtrumPumpProvider: Provider<MedtrumPump>,
    private val danaRPluginProvider: Provider<DanaRPlugin>,
    private val danaRKoreanPluginProvider: Provider<DanaRKoreanPlugin>,
    private val danaRv2PluginProvider: Provider<DanaRv2Plugin>,
    private val insightPluginProvider: Provider<InsightPlugin>,
    private val medtronicPumpPluginProvider: Provider<MedtronicPumpPlugin>,
    private val diaconnG8PluginProvider: Provider<DiaconnG8Plugin>,
    private val eopatchPumpPluginProvider: Provider<EopatchPumpPlugin>,
    private val rileyLinkUtilProvider: Provider<RileyLinkUtil>,
    private val rileyLinkServiceDataProvider: Provider<RileyLinkServiceData>,
    private val serviceTaskExecutorProvider: Provider<ServiceTaskExecutor>,
    private val medtronicHistoryDataProvider: Provider<MedtronicHistoryData>,
    private val medtronicPumpStatusProvider: Provider<MedtronicPumpStatus>,
    private val medtronicUtilProvider: Provider<MedtronicUtil>,
    private val patchManagerProvider: Provider<IPatchManager>,
    private val patchManagerExecutorProvider: Provider<PatchManagerExecutor>,
    private val patchConfigProvider: Provider<PatchConfig>,
    private val tempBasalManagerProvider: Provider<TempBasalManager>,
    private val normalBasalManagerProvider: Provider<NormalBasalManager>,
    private val preferenceManagerProvider: Provider<PreferenceManager>,
    private val alarmRegistryProvider: Provider<IAlarmRegistry>,
    private val rxActionProvider: Provider<RxAction>,
    private val omnipodDashPumpPluginProvider: Provider<OmnipodDashPumpPlugin>,
    private val omnipodErosPumpPluginProvider: Provider<OmnipodErosPumpPlugin>
) {

    @Provides fun bleTransport(): BleTransport = bleTransportProvider.get()
    @Provides fun rfcommTransport(): RfcommTransport = rfcommTransportProvider.get()
    @Provides fun danaHistoryRecordDao(): DanaHistoryRecordDao = danaHistoryRecordDaoProvider.get()
    @Provides fun diaconnHistoryRecordDao(): DiaconnHistoryRecordDao = diaconnHistoryRecordDaoProvider.get()
    @Provides fun diaconnHistoryDatabase(): DiaconnHistoryDatabase = diaconnHistoryDatabaseProvider.get()
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
    @Provides fun bleComm(): BLEComm = bleCommProvider.get()
    @Provides fun comboV2Plugin(): ComboV2Plugin = comboV2PluginProvider.get()
    @Provides fun danaPump(): DanaPump = danaPumpProvider.get()
    @Provides fun danaRSPlugin(): DanaRSPlugin = danaRSPluginProvider.get()
    @Provides fun diaconnG8Pump(): DiaconnG8Pump = diaconnG8PumpProvider.get()
    @Provides fun equilManager(): EquilManager = equilManagerProvider.get()
    @Provides fun equilPumpPlugin(): EquilPumpPlugin = equilPumpPluginProvider.get()
    @Provides fun medtrumPlugin(): MedtrumPlugin = medtrumPluginProvider.get()
    @Provides fun medtrumPump(): MedtrumPump = medtrumPumpProvider.get()
    @Provides fun omnipodDashPumpPlugin(): OmnipodDashPumpPlugin = omnipodDashPumpPluginProvider.get()
    @Provides fun danaRPlugin(): DanaRPlugin = danaRPluginProvider.get()
    @Provides fun danaRKoreanPlugin(): DanaRKoreanPlugin = danaRKoreanPluginProvider.get()
    @Provides fun danaRv2Plugin(): DanaRv2Plugin = danaRv2PluginProvider.get()
    @Provides fun insightPlugin(): InsightPlugin = insightPluginProvider.get()
    @Provides fun medtronicPumpPlugin(): MedtronicPumpPlugin = medtronicPumpPluginProvider.get()
    @Provides fun diaconnG8Plugin(): DiaconnG8Plugin = diaconnG8PluginProvider.get()
    @Provides fun eopatchPumpPlugin(): EopatchPumpPlugin = eopatchPumpPluginProvider.get()
    @Provides fun rileyLinkUtil(): RileyLinkUtil = rileyLinkUtilProvider.get()
    @Provides fun rileyLinkServiceData(): RileyLinkServiceData = rileyLinkServiceDataProvider.get()
    @Provides fun serviceTaskExecutor(): ServiceTaskExecutor = serviceTaskExecutorProvider.get()
    @Provides fun medtronicHistoryData(): MedtronicHistoryData = medtronicHistoryDataProvider.get()
    @Provides fun medtronicPumpStatus(): MedtronicPumpStatus = medtronicPumpStatusProvider.get()
    @Provides fun medtronicUtil(): MedtronicUtil = medtronicUtilProvider.get()
    @Provides fun patchManager(): IPatchManager = patchManagerProvider.get()
    @Provides fun patchManagerExecutor(): PatchManagerExecutor = patchManagerExecutorProvider.get()
    @Provides fun patchConfig(): PatchConfig = patchConfigProvider.get()
    @Provides fun tempBasalManager(): TempBasalManager = tempBasalManagerProvider.get()
    @Provides fun normalBasalManager(): NormalBasalManager = normalBasalManagerProvider.get()
    @Provides fun preferenceManager(): PreferenceManager = preferenceManagerProvider.get()
    @Provides fun alarmRegistry(): IAlarmRegistry = alarmRegistryProvider.get()
    @Provides fun rxAction(): RxAction = rxActionProvider.get()
    @Provides fun omnipodErosPumpPlugin(): OmnipodErosPumpPlugin = omnipodErosPumpPluginProvider.get()
}
