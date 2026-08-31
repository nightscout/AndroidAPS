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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.pump.combov2.ComboV2Plugin
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
        rfcommTransport: Provider<RfcommTransport>,
        danaHistoryRecordDao: Provider<DanaHistoryRecordDao>,
        diaconnHistoryRecordDao: Provider<DiaconnHistoryRecordDao>,
        diaconnHistoryDatabase: Provider<DiaconnHistoryDatabase>,
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
        danaRSPackets: Provider<Set<DanaRSPacket>>,
        comboV2Plugin: Provider<ComboV2Plugin>,
        danaRSPlugin: Provider<DanaRSPlugin>,
        equilPumpPlugin: Provider<EquilPumpPlugin>,
        medtrumPlugin: Provider<MedtrumPlugin>,
        danaRPlugin: Provider<DanaRPlugin>,
        danaRKoreanPlugin: Provider<DanaRKoreanPlugin>,
        danaRv2Plugin: Provider<DanaRv2Plugin>,
        insightPlugin: Provider<InsightPlugin>,
        medtronicPumpPlugin: Provider<MedtronicPumpPlugin>,
        diaconnG8Plugin: Provider<DiaconnG8Plugin>,
        eopatchPumpPlugin: Provider<EopatchPumpPlugin>,
        patchManager: Provider<IPatchManager>,
        patchManagerExecutor: Provider<PatchManagerExecutor>,
        patchConfig: Provider<PatchConfig>,
        tempBasalManager: Provider<TempBasalManager>,
        normalBasalManager: Provider<NormalBasalManager>,
        preferenceManager: Provider<PreferenceManager>,
        alarmRegistry: Provider<IAlarmRegistry>,
        omnipodDashPumpPlugin: Provider<OmnipodDashPumpPlugin>,
        omnipodErosPumpPlugin: Provider<OmnipodErosPumpPlugin>,
        erosHistory: Provider<ErosHistory>,
        erosPodStateManager: Provider<ErosPodStateManager>,
        aapsErosPodStateManager: Provider<AapsErosPodStateManager>,
        aapsOmnipodErosManager: Provider<AapsOmnipodErosManager>,
        aapsOmnipodUtil: Provider<AapsOmnipodUtil>,
        omnipodAlertUtil: Provider<OmnipodAlertUtil>
    ): PumpLeaves = PumpLeaves(
        bleTransport, rfcommTransport, danaHistoryRecordDao, diaconnHistoryRecordDao, diaconnHistoryDatabase,
        equilBleTransport, equilHistoryPumpDao, equilHistoryRecordDao, dashHistoryDatabase, historyMapper, historyRecordDao,
        medtrumBleTransport, omnipodDashManager, omnipodDashPodStateManager, danaHistoryDatabase, danaRSPackets,
        comboV2Plugin, danaRSPlugin, equilPumpPlugin,
        medtrumPlugin,
        danaRPlugin, danaRKoreanPlugin, danaRv2Plugin, insightPlugin, medtronicPumpPlugin, diaconnG8Plugin,
        eopatchPumpPlugin,


        patchManager, patchManagerExecutor, patchConfig, tempBasalManager, normalBasalManager,
        preferenceManager, alarmRegistry,
        omnipodDashPumpPlugin, omnipodErosPumpPlugin, erosHistory, erosPodStateManager,
        aapsErosPodStateManager, aapsOmnipodErosManager, aapsOmnipodUtil, omnipodAlertUtil
    )
}
