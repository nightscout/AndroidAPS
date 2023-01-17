package info.nightscout.plugins.sync.dataBroadcaster

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BundleMock
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.CobInfo
import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito

internal class DataBroadcastPluginTest : TestBaseWithProfile() {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var loop: Loop
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var sut: DataBroadcastPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }
    private val testPumpPlugin = TestPumpPlugin(injector)

    @BeforeEach
    fun setUp() {
        sut = DataBroadcastPlugin(
            injector, aapsLogger, rh, aapsSchedulers, context, dateUtil, fabricPrivacy, rxBus, iobCobCalculator, profileFunction, defaultValueHelper, processedDeviceStatusData,
            loop, activePlugin, receiverStatusStore, config, glucoseStatusProvider
        )
        Mockito.`when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        Mockito.`when`(autosensDataStore.lastBg()).thenReturn(InMemoryGlucoseValue(1000, 100.0))
        Mockito.`when`(profileFunction.getProfile()).thenReturn(validProfile)
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        Mockito.`when`(profileFunction.getProfileName()).thenReturn("TestProfile")
        Mockito.`when`(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(System.currentTimeMillis()))
        Mockito.`when`(iobCobCalculator.getCobInfo("broadcast")).thenReturn(CobInfo(1000, 100.0, 10.0))
        Mockito.`when`(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(System.currentTimeMillis()))
        Mockito.`when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(TemporaryBasal(timestamp = 1000, duration = 60000, isAbsolute = true, rate = 1.0, type = TemporaryBasal.Type.NORMAL))
        Mockito.`when`(processedDeviceStatusData.uploaderStatus).thenReturn("100%")
        Mockito.`when`(loop.lastRun).thenReturn(Loop.LastRun().also {
            it.lastTBREnact = 1000
            it.tbrSetByPump = PumpEnactResult(injector).success(true).enacted(true)
        }
        )
        Mockito.`when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        Mockito.`when`(glucoseStatusProvider.glucoseStatusData).thenReturn(GlucoseStatus(100.0))
        Mockito.`when`(processedDeviceStatusData.openAPSData).thenReturn(ProcessedDeviceStatusData.OpenAPSData().also {
            it.clockSuggested = 1000L
            it.suggested = JSONObject()
            it.clockEnacted = 1000L
            it.enacted = JSONObject()
        })
    }

    @Test
    fun prepareDataTestAPS() {
        Mockito.`when`(config.APS).thenReturn(true)
        val event = EventOverviewBolusProgress.also {
            it.status = "Some status"
            it.percent = 100
        }
        val bundle = BundleMock.mock()
        sut.prepareData(event, bundle)
        Assertions.assertTrue(bundle.containsKey("progressPercent"))
        Assertions.assertTrue(bundle.containsKey("progressStatus"))
        Assertions.assertTrue(bundle.containsKey("glucoseMgdl"))
        Assertions.assertTrue(bundle.containsKey("glucoseTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("units"))
        Assertions.assertTrue(bundle.containsKey("slopeArrow"))
        Assertions.assertTrue(bundle.containsKey("deltaMgdl"))
        Assertions.assertTrue(bundle.containsKey("avgDeltaMgdl"))
        Assertions.assertTrue(bundle.containsKey("high"))
        Assertions.assertTrue(bundle.containsKey("low"))
        Assertions.assertTrue(bundle.containsKey("bolusIob"))
        Assertions.assertTrue(bundle.containsKey("basalIob"))
        Assertions.assertTrue(bundle.containsKey("iob"))
        Assertions.assertTrue(bundle.containsKey("cob"))
        Assertions.assertTrue(bundle.containsKey("futureCarbs"))
        Assertions.assertTrue(bundle.containsKey("phoneBattery"))
        Assertions.assertTrue(bundle.containsKey("rigBattery"))
        Assertions.assertTrue(bundle.containsKey("suggestedTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("suggested"))
        Assertions.assertTrue(bundle.containsKey("enactedTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("enacted"))
        Assertions.assertTrue(bundle.containsKey("basalTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("baseBasal"))
        Assertions.assertTrue(bundle.containsKey("profile"))
        Assertions.assertTrue(bundle.containsKey("tempBasalStart"))
        Assertions.assertTrue(bundle.containsKey("tempBasalDurationInMinutes"))
        Assertions.assertTrue(bundle.containsKey("tempBasalString"))
        Assertions.assertTrue(bundle.containsKey("pumpTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("pumpBattery"))
        Assertions.assertTrue(bundle.containsKey("pumpReservoir"))
        Assertions.assertTrue(bundle.containsKey("pumpStatus"))
    }

    @Test
    fun prepareDataTestAAPSClient() {
        Mockito.`when`(config.APS).thenReturn(false)
        val event = EventOverviewBolusProgress.also {
            it.status = "Some status"
            it.percent = 100
        }
        val bundle = BundleMock.mock()
        sut.prepareData(event, bundle)
        Assertions.assertTrue(bundle.containsKey("progressPercent"))
        Assertions.assertTrue(bundle.containsKey("progressStatus"))
        Assertions.assertTrue(bundle.containsKey("glucoseMgdl"))
        Assertions.assertTrue(bundle.containsKey("glucoseTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("units"))
        Assertions.assertTrue(bundle.containsKey("slopeArrow"))
        Assertions.assertTrue(bundle.containsKey("deltaMgdl"))
        Assertions.assertTrue(bundle.containsKey("avgDeltaMgdl"))
        Assertions.assertTrue(bundle.containsKey("high"))
        Assertions.assertTrue(bundle.containsKey("low"))
        Assertions.assertTrue(bundle.containsKey("bolusIob"))
        Assertions.assertTrue(bundle.containsKey("basalIob"))
        Assertions.assertTrue(bundle.containsKey("iob"))
        Assertions.assertTrue(bundle.containsKey("cob"))
        Assertions.assertTrue(bundle.containsKey("futureCarbs"))
        Assertions.assertTrue(bundle.containsKey("phoneBattery"))
        Assertions.assertTrue(bundle.containsKey("rigBattery"))
        Assertions.assertTrue(bundle.containsKey("suggestedTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("suggested"))
        Assertions.assertTrue(bundle.containsKey("enactedTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("enacted"))
        Assertions.assertTrue(bundle.containsKey("basalTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("baseBasal"))
        Assertions.assertTrue(bundle.containsKey("profile"))
        Assertions.assertTrue(bundle.containsKey("tempBasalStart"))
        Assertions.assertTrue(bundle.containsKey("tempBasalDurationInMinutes"))
        Assertions.assertTrue(bundle.containsKey("tempBasalString"))
        Assertions.assertTrue(bundle.containsKey("pumpTimeStamp"))
        Assertions.assertTrue(bundle.containsKey("pumpBattery"))
        Assertions.assertTrue(bundle.containsKey("pumpReservoir"))
        Assertions.assertTrue(bundle.containsKey("pumpStatus"))
    }
}