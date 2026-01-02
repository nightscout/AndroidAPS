package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.BooleanKey
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.keys.NsclientStringKey
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("SpellCheckingInspection")
internal class NSClientV3PluginTest : TestBaseWithProfile() {

    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var virtualPump: VirtualPump
    @Mock lateinit var mockedProfileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var insulin: Insulin
    @Mock lateinit var l: L
    @Mock lateinit var nsClientV3Service: NSClientV3Service

    private lateinit var storeDataForDb: StoreDataForDbImpl
    private lateinit var sut: NSClientV3Plugin

    private var insulinConfiguration: ICfg = ICfg("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun prepare() {
        whenever(insulin.iCfg).thenReturn(insulinConfiguration)
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
        storeDataForDb = StoreDataForDbImpl(aapsLogger, rxBus, persistenceLayer, preferences, config, nsClientSource, virtualPump)
        sut =
            NSClientV3Plugin(
                aapsLogger, rh, preferences, aapsSchedulers, rxBus, context, fabricPrivacy,
                receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
                nsClientSource, storeDataForDb, decimalFormatter, l
            )
        sut.nsAndroidClient = nsAndroidClient
        sut.nsClientV3Service = nsClientV3Service
        whenever(mockedProfileFunction.getProfile(anyLong())).thenReturn(validProfile)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddDeviceStatus() = runTest {
        val deviceStatus = DS(
            timestamp = 10000,
            suggested = "{\"temp\":\"absolute\",\"bg\":133,\"tick\":-6,\"eventualBG\":67,\"targetBG\":99,\"insulinReq\":0,\"deliverAt\":\"2023-01-02T15:29:33.374Z\",\"sensitivityRatio\":1,\"variable_sens\":97.5,\"predBGs\":{\"IOB\":[133,127,121,116,111,106,101,97,93,89,85,81,78,75,72,69,67,65,62,60,58,57,55,54,52,51,50,49,48,47,46,45,45,44,43,43,42,42,41,41,41,41,40,40,40,40,39],\"ZT\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39,39,39,39,39,39,39],\"UAM\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39]},\"reason\":\"COB: 0, Dev: 0.1, BGI: -0.3, ISF: 5.4, CR: 13, Target: 5.5, minPredBG 2.2, minGuardBG 2.1, IOBpredBG 2.2, UAMpredBG 2.2; minGuardBG 2.1<4.0\",\"COB\":0,\"IOB\":0.692,\"duration\":90,\"rate\":0,\"timestamp\":\"2023-01-02T15:29:39.460Z\"}",
            iob = "{\"iob\":0.692,\"basaliob\":-0.411,\"activity\":0.0126,\"time\":\"2023-01-02T15:29:39.460Z\"}",
            enacted = "{\"temp\":\"absolute\",\"bg\":133,\"tick\":-6,\"eventualBG\":67,\"targetBG\":99,\"insulinReq\":0,\"deliverAt\":\"2023-01-02T15:29:33.374Z\",\"sensitivityRatio\":1,\"variable_sens\":97.5,\"predBGs\":{\"IOB\":[133,127,121,116,111,106,101,97,93,89,85,81,78,75,72,69,67,65,62,60,58,57,55,54,52,51,50,49,48,47,46,45,45,44,43,43,42,42,41,41,41,41,40,40,40,40,39],\"ZT\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39,39,39,39,39,39,39],\"UAM\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39]},\"reason\":\"COB: 0, Dev: 0.1, BGI: -0.3, ISF: 5.4, CR: 13, Target: 5.5, minPredBG 2.2, minGuardBG 2.1, IOBpredBG 2.2, UAMpredBG 2.2; minGuardBG 2.1<4.0\",\"COB\":0,\"IOB\":0.692,\"duration\":90,\"rate\":0,\"timestamp\":\"2023-01-02T15:29:39.460Z\"}",
            device = "openaps://samsung SM-G970F",
            pump = "{\"battery\":{\"percent\":75},\"status\":{\"status\":\"normal\",\"timestamp\":\"2023-01-02T15:20:20.656Z\"},\"extended\":{\"Version\":\"3.1.0.3-dev-e-295e1ad18f-2022.12.24\"," +
                "\"LastBolus\":\"02.01.23 15:24\",\"LastBolusAmount\":\"1\",\"TempBasalAbsoluteRate\":\"0\",\"TempBasalStart\":\"02.01.23 16:20\",\"TempBasalRemaining\":\"55\",\"BaseBasalRate\":\"0" +
                ".41\",\"ActiveProfile\":\"L29_U200 IC\"},\"reservoir\":\"133\",\"clock\":\"2023-01-02T15:25:05.826Z\"}",
            uploaderBattery = 60,
            isCharging = false,
            configuration = "{\"insulin\":5,\"insulinConfiguration\":{},\"sensitivity\":2,\"sensitivityConfiguration\":{\"openapsama_min_5m_carbimpact\":8,\"absorption_cutoff\":4,\"autosens_max\":1.2,\"autosens_min\":0.7},\"overviewConfiguration\":{\"units\":\"mmol\",\"QuickWizard\":\"[]\",\"eatingsoon_duration\":60,\"eatingsoon_target\":4,\"activity_duration\":180,\"activity_target\":7.5,\"hypo_duration\":90,\"hypo_target\":8,\"low_mark\":3.9,\"high_mark\":10,\"statuslights_cage_warning\":72,\"statuslights_cage_critical\":96,\"statuslights_iage_warning\":120,\"statuslights_iage_critical\":150,\"statuslights_sage_warning\":168,\"statuslights_sage_critical\":336,\"statuslights_sbat_warning\":25,\"statuslights_sbat_critical\":5,\"statuslights_bage_warning\":720,\"statuslights_bage_critical\":800,\"statuslights_res_warning\":30,\"statuslights_res_critical\":10,\"statuslights_bat_warning\":50,\"statuslights_bat_critical\":25,\"boluswizard_percentage\":70},\"safetyConfiguration\":{\"age\":\"resistantadult\",\"treatmentssafety_maxbolus\":10,\"treatmentssafety_maxcarbs\":70}}"
        )
        val dataPair = DataSyncSelector.PairDeviceStatus(deviceStatus, 1000)
        // create
        whenever(nsAndroidClient.createDeviceStatus(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("devicestatus", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdDeviceStatuses).hasSize(1)
        // update
        whenever(nsAndroidClient.createDeviceStatus(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsAdd("devicestatus", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdDeviceStatuses).hasSize(2) // still only 1
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddEntries() = runTest {
        val glucoseValue = GV(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
            ids = IDs(nightscoutId = "nightscoutId")
        )
        val dataPair = DataSyncSelector.PairGlucoseValue(glucoseValue, 1000)
        // create
        whenever(nsAndroidClient.createSgv(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("entries", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdGlucoseValues).hasSize(1)
        // update
        whenever(nsAndroidClient.updateSvg(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("entries", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdGlucoseValues).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddFood() = runTest {
        val food = FD(
            isValid = true,
            name = "name",
            category = "category",
            subCategory = "subcategory",
            portion = 2.0,
            carbs = 20,
            fat = 21,
            protein = 22,
            energy = 23,
            unit = "g",
            gi = 25,
            ids = IDs(
                nightscoutId = "nightscoutId"
            )
        )
        val dataPair = DataSyncSelector.PairFood(food, 1000)
        // create
        whenever(nsAndroidClient.createFood(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("food", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdFoods).hasSize(1)
        // update
        whenever(nsAndroidClient.updateFood(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("food", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdFoods).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddBolus() = runTest {
        val bolus = BS(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            type = BS.Type.SMB,
            notes = "aaaa",
            isBasalInsulin = false,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairBolus(bolus, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBoluses).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBoluses).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddCarbs() = runTest {
        val carbs = CA(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            duration = 0,
            notes = "aaaa",
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairCarbs(carbs, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdCarbs).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdCarbs).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddBolusCalculatorResult() = runTest {
        val bolus = BCR(
            timestamp = 10000,
            isValid = true,
            targetBGLow = 110.0,
            targetBGHigh = 120.0,
            isf = 30.0,
            ic = 2.0,
            bolusIOB = 1.1,
            wasBolusIOBUsed = true,
            basalIOB = 1.2,
            wasBasalIOBUsed = true,
            glucoseValue = 150.0,
            wasGlucoseUsed = true,
            glucoseDifference = 30.0,
            glucoseInsulin = 1.3,
            glucoseTrend = 15.0,
            wasTrendUsed = true,
            trendInsulin = 1.4,
            cob = 24.0,
            wasCOBUsed = true,
            cobInsulin = 1.5,
            carbs = 36.0,
            wereCarbsUsed = true,
            carbsInsulin = 1.6,
            otherCorrection = 1.7,
            wasSuperbolusUsed = true,
            superbolusInsulin = 0.3,
            wasTempTargetUsed = false,
            totalInsulin = 9.1,
            percentageCorrection = 70,
            profileName = " sss",
            note = "ddd",
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairBolusCalculatorResult(bolus, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBolusCalculatorResults).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBolusCalculatorResults).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddEffectiveProfileSwitch() = runTest {
        val profileSwitch = EPS(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = validProfile.units,
            originalProfileName = "SomeProfile",
            originalCustomizedName = "SomeProfile (150%, 1h)",
            originalTimeshift = 3600000,
            originalPercentage = 150,
            originalDuration = 3600000,
            originalEnd = 0,
            iCfg = activePlugin.activeInsulin.iCfg.also {
                it.insulinEndTime = (validProfile.dia * 3600 * 1000).toLong()
            },
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairEffectiveProfileSwitch(profileSwitch, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdEffectiveProfileSwitches).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdEffectiveProfileSwitches).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddProfileSwitch() = runTest {
        val profileSwitch = PS(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = validProfile.units,
            profileName = "SomeProfile",
            timeshift = 0,
            percentage = 100,
            duration = 0,
            iCfg = activePlugin.activeInsulin.iCfg.also {
                it.insulinEndTime = (validProfile.dia * 3600 * 1000).toLong()
            },
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairProfileSwitch(profileSwitch, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdProfileSwitches).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdProfileSwitches).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddExtendedBolus() = runTest {
        val extendedBolus = EB(
            timestamp = 10000,
            isValid = true,
            amount = 2.0,
            isEmulatingTempBasal = false,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairExtendedBolus(extendedBolus, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdExtendedBoluses).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdExtendedBoluses).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddRunningModeTest() = runTest {
        val runningMode = RM(
            timestamp = 10000,
            isValid = true,
            mode = RM.Mode.DISCONNECTED_PUMP,
            duration = 30000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairRunningMode(runningMode, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdRunningModes).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdRunningModes).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddTemporaryBasal() = runTest {
        val temporaryBasal = TB(
            timestamp = 10000,
            isValid = true,
            type = TB.Type.NORMAL,
            rate = 2.0,
            isAbsolute = true,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairTemporaryBasal(temporaryBasal, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdTemporaryBasals).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdTemporaryBasals).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddTemporaryTarget() = runTest {
        val temporaryTarget = TT(
            timestamp = 10000,
            isValid = true,
            reason = TT.Reason.ACTIVITY,
            highTarget = 100.0,
            lowTarget = 99.0,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairTemporaryTarget(temporaryTarget, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTemporaryTargets).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTemporaryTargets).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddTherapyEvent() = runTest {
        val therapyEvent = TE(
            timestamp = 10000,
            isValid = true,
            type = TE.Type.ANNOUNCEMENT,
            note = "ccccc",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TE.MeterType.FINGER,
            glucoseUnit = GlucoseUnit.MGDL,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairTherapyEvent(therapyEvent, 1000)
        // create
        whenever(nsAndroidClient.createTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTherapyEvents).hasSize(1)
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTherapyEvents).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddProfile() = runTest {

        val dataPair = DataSyncSelector.PairProfileStore(getValidProfileStore().getData(), 1000)
        // create
        whenever(nsAndroidClient.createProfileStore(anyOrNull())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("profile", dataPair, "1/3")
        // verify(dataSyncSelectorV3, Times(1)).confirmLastProfileStore(1000)
        // verify(dataSyncSelectorV3, Times(1)).processChangedProfileStore()
        // update
        whenever(nsAndroidClient.updateTreatment(anyOrNull())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("profile", dataPair, "1/3")
        // verify(dataSyncSelectorV3, Times(2)).confirmLastProfileStore(1000)
        // verify(dataSyncSelectorV3, Times(2)).processChangedProfileStore()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        sut.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    @Test
    fun `resetToFullSync should clear sync timestamps and reset initialLoadFinished flag`() {
        // Arrange
        // 1. Set the plugin's state to a "synced" status to ensure the reset works.
        sut.initialLoadFinished = true
        sut.lastLoadedSrvModified = LastModified(
            LastModified.Collections().apply {
                treatments = 1672531200000L // Jan 1, 2023
                devicestatus = 1672531200000L
                profile = 1672531200000L
                foods = 1672531200000L
                entries = 1672531200000L
            }
        )
        sut.firstLoadContinueTimestamp = sut.lastLoadedSrvModified

        // 2. Define the expected state after reset (an empty LastModified object as a JSON string).
        val expectedEmptyLastModifiedJson = "{\"collections\":{}}"

        // Act
        sut.resetToFullSync()

        // Assert
        // 1. Verify the in-memory flags and objects are reset to their default states.
        assertThat(sut.initialLoadFinished).isFalse()
        assertThat(sut.fullSyncRequested).isTrue()
        assertThat(sut.lastLoadedSrvModified.collections.treatments).isEqualTo(0L)
        assertThat(sut.firstLoadContinueTimestamp.collections.treatments).isEqualTo(0L)

        // 2. Verify that the empty state was written to preferences to make the reset persistent.
        verify(preferences).put(NsclientStringKey.V3LastModified, expectedEmptyLastModifiedJson)
        verify(dataSyncSelectorV3).resetToNextFullSync()
    }

    @Test
    fun `isFirstLoad returns true when collection timestamp is 0`() {
        // Arrange
        // The plugin is initialized with lastLoadedSrvModified where all timestamps are 0L by default.
        // So, no extra arrangement is needed.

        // Act & Assert
        // Verify that isFirstLoad returns true for all collections in the initial state.
        assertThat(sut.isFirstLoad(NsClient.Collection.ENTRIES)).isTrue()
        assertThat(sut.isFirstLoad(NsClient.Collection.TREATMENTS)).isTrue()
        assertThat(sut.isFirstLoad(NsClient.Collection.FOODS)).isTrue()
        assertThat(sut.isFirstLoad(NsClient.Collection.PROFILE)).isTrue()
    }

    @Test
    fun `isFirstLoad returns false when collection timestamp is greater than 0`() {
        // Arrange
        // Set a "synced" state where all collection timestamps are non-zero.
        val collectionsWithData =
            LastModified.Collections().apply {
                treatments = 1672531200000L // Jan 1, 2023
                devicestatus = 1672531200000L
                profile = 1672531200000L
                foods = 1672531200000L
                entries = 1672531200000L
            }

        sut.lastLoadedSrvModified = LastModified(collectionsWithData)

        // Act & Assert
        // Verify that isFirstLoad now returns false for all collections.
        assertThat(sut.isFirstLoad(NsClient.Collection.ENTRIES)).isFalse()
        assertThat(sut.isFirstLoad(NsClient.Collection.TREATMENTS)).isFalse()
        assertThat(sut.isFirstLoad(NsClient.Collection.FOODS)).isFalse()
        assertThat(sut.isFirstLoad(NsClient.Collection.PROFILE)).isFalse()
    }

    @Test
    fun `isFirstLoad returns correct value for mixed state`() {
        // Arrange
        // Set a state where only Treatments and Profile have been synced. Entries and Foods have not.
        val collectionsWithData = LastModified.Collections(
            entries = 0L, // Not synced
            treatments = 1672531200001L, // Synced
            foods = 0L, // Not synced
            profile = 1672531200003L // Synced
        )
        sut.lastLoadedSrvModified = LastModified(collectionsWithData)

        // Act & Assert
        // Verify the result for each collection individually.
        assertThat(sut.isFirstLoad(NsClient.Collection.ENTRIES)).isTrue()
        assertThat(sut.isFirstLoad(NsClient.Collection.TREATMENTS)).isFalse()
        assertThat(sut.isFirstLoad(NsClient.Collection.FOODS)).isTrue()
        assertThat(sut.isFirstLoad(NsClient.Collection.PROFILE)).isFalse()
    }

    @Test
    fun `updateLatestBgReceivedIfNewer SETS timestamp during a first load`() {
        // Arrange
        // 1. Ensure isFirstLoad(ENTRIES) will return true. The default initial state is 0L, so no setup is needed.
        val latestReceivedTimestamp = 1672531200000L // Jan 1, 2023
        assertThat(sut.isFirstLoad(NsClient.Collection.ENTRIES)).isTrue()
        assertThat(sut.firstLoadContinueTimestamp.collections.entries).isEqualTo(0L)

        // Act
        sut.updateLatestBgReceivedIfNewer(latestReceivedTimestamp)

        // Assert
        // Verify that the firstLoadContinueTimestamp for entries was updated.
        assertThat(sut.firstLoadContinueTimestamp.collections.entries).isEqualTo(latestReceivedTimestamp)
    }

    @Test
    fun `updateLatestBgReceivedIfNewer IGNORES timestamp after a first load`() {
        // Arrange
        // 1. Set a state where ENTRIES have already been synced.
        val initialTimestamp = 1672531200000L
        sut.lastLoadedSrvModified = LastModified(
            LastModified.Collections(entries = initialTimestamp)
        )
        val newTimestamp = initialTimestamp + 1000L
        assertThat(sut.isFirstLoad(NsClient.Collection.ENTRIES)).isFalse()

        // Act
        sut.updateLatestBgReceivedIfNewer(newTimestamp)

        // Assert
        // Verify that the firstLoadContinueTimestamp for entries remains unchanged (at its default 0L).
        assertThat(sut.firstLoadContinueTimestamp.collections.entries).isEqualTo(0L)
    }

    @Test
    fun `updateLatestTreatmentReceivedIfNewer SETS timestamp during a first load`() {
        // Arrange
        // 1. Ensure isFirstLoad(TREATMENTS) will return true.
        val latestReceivedTimestamp = 1672541200000L
        assertThat(sut.isFirstLoad(NsClient.Collection.TREATMENTS)).isTrue()
        assertThat(sut.firstLoadContinueTimestamp.collections.treatments).isEqualTo(0L)

        // Act
        sut.updateLatestTreatmentReceivedIfNewer(latestReceivedTimestamp)

        // Assert
        // Verify that the firstLoadContinueTimestamp for treatments was updated.
        assertThat(sut.firstLoadContinueTimestamp.collections.treatments).isEqualTo(latestReceivedTimestamp)
    }

    @Test
    fun `updateLatestTreatmentReceivedIfNewer IGNORES timestamp after a first load`() {
        // Arrange
        // 1. Set a state where TREATMENTS have already been synced.
        val initialTimestamp = 1672541200000L
        sut.lastLoadedSrvModified = LastModified(
            LastModified.Collections(treatments = initialTimestamp)
        )
        val newTimestamp = initialTimestamp + 1000L
        assertThat(sut.isFirstLoad(NsClient.Collection.TREATMENTS)).isFalse()

        // Act
        sut.updateLatestTreatmentReceivedIfNewer(newTimestamp)

        // Assert
        // Verify that the firstLoadContinueTimestamp for treatments remains unchanged (at its default 0L).
        assertThat(sut.firstLoadContinueTimestamp.collections.treatments).isEqualTo(0L)
    }

    @Test
    fun `handleClearAlarm delegates to service when plugin and upload are enabled`() {
        // Arrange
        //1. Mock the original alarm object
        val mockAlarm: NSAlarm = mock()
        val silenceDuration = T.mins(15).msecs()
        val lastModified = Json.encodeToString(LastModified.serializer(), LastModified(LastModified.Collections()))

        // 2. Ensure both the plugin and the upload preference are enabled.
        whenever(preferences.get(NsclientStringKey.V3LastModified)).thenReturn(lastModified)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)
        whenever(receiverDelegate.blockingReason).thenReturn("Block")
        sut.setPluginEnabledBlocking(PluginType.SYNC, true)

        // Act
        sut.handleClearAlarm(mockAlarm, silenceDuration)

        // Assert
        // Verify that the call was passed through to the nsClientV3Service with the correct parameters.
        verify(nsClientV3Service).handleClearAlarm(mockAlarm, silenceDuration)
        sut.setPluginEnabledBlocking(PluginType.SYNC, false)
    }

    @Test
    fun `handleClearAlarm does nothing when plugin is disabled`() {
        // Arrange
        val mockAlarm: NSAlarm = mock()
        val silenceDuration = T.mins(15).msecs()
        val lastModified = Json.encodeToString(LastModified.serializer(), LastModified(LastModified.Collections()))

        // 2. Ensure both the plugin and the upload preference are enabled.
        whenever(preferences.get(NsclientStringKey.V3LastModified)).thenReturn(lastModified)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true) // Upload is still enabled
        whenever(receiverDelegate.blockingReason).thenReturn("Block")
        sut.setPluginEnabledBlocking(PluginType.SYNC, false)

        // Act
        sut.handleClearAlarm(mockAlarm, silenceDuration)

        // Assert
        // Verify that the service was never called.
        verify(nsClientV3Service, never()).handleClearAlarm(any(), any())
    }

    @Test
    fun `handleClearAlarm does nothing and logs when upload is disabled`() {
        // Arrange
        val mockAlarm: NSAlarm = mock()
        val silenceDuration = T.mins(15).msecs()

        // 2. Ensure the plugin is enabled, but the upload preference is disabled.
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(false)

        // Act
        sut.handleClearAlarm(mockAlarm, silenceDuration)

        // Assert
        // 1. Verify that the service was never called.
        verify(nsClientV3Service, never()).handleClearAlarm(any(), any())
    }
}
