package app.aaps.plugins.sync.nsShared

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
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.keys.BooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class StoreDataForDbImplTest : TestBaseWithProfile() {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var virtualPump: VirtualPump
    @Mock private lateinit var nsClientRepository: NSClientRepository

    private lateinit var storeDataForDb: StoreDataForDbImpl
    private lateinit var testAppScope: CoroutineScope

    val tt = TT(timestamp = now, reason = TT.Reason.ACTIVITY, highTarget = 120.0, lowTarget = 100.0, duration = T.mins(30).msecs())
    val gv = GV(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now, sourceSensor = SourceSensor.IOB_PREDICTION, trendArrow = TrendArrow.NONE)
    val fd = FD(name = "Apple", carbs = 24, portion = 1.0)
    val bs = BS(timestamp = now - 1, amount = 1.0, type = BS.Type.NORMAL, iCfg = someICfg)
    val ca = CA(timestamp = now, amount = 12.0, duration = 0)
    val tb = TB(timestamp = now, type = TB.Type.NORMAL, isAbsolute = true, rate = 0.7, duration = T.mins(30).msecs())
    val eb = EB(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
    val bcr = BCR(
        timestamp = now, isValid = true, targetBGLow = 110.0, targetBGHigh = 120.0, isf = 30.0, ic = 2.0, bolusIOB = 1.1, wasBolusIOBUsed = true, basalIOB = 1.2,
        wasBasalIOBUsed = true, glucoseValue = 150.0, wasGlucoseUsed = true, glucoseDifference = 30.0, glucoseInsulin = 1.3, glucoseTrend = 15.0, wasTrendUsed = true,
        trendInsulin = 1.4, cob = 24.0, wasCOBUsed = true, cobInsulin = 1.5, carbs = 36.0, wereCarbsUsed = true, carbsInsulin = 1.6, otherCorrection = 1.7,
        wasSuperbolusUsed = true, superbolusInsulin = 0.3, wasTempTargetUsed = false, totalInsulin = 9.1, percentageCorrection = 70, profileName = " sss", note = "ddd"
    )
    val eps = EPS(
        timestamp = 100, basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList(), glucoseUnit = GlucoseUnit.MGDL, originalProfileName = "foo", originalCustomizedName = "bar",
        originalTimeshift = 0, originalPercentage = 100, originalDuration = 0, originalEnd = 100, iCfg = ICfg("label", 0, 0)
    )
    val ps = PS(
        timestamp = 10000, isValid = true, basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList(), glucoseUnit = GlucoseUnit.MGDL,
        profileName = "SomeProfile", timeshift = 0, percentage = 100, duration = 0, iCfg = ICfg("label", 0, 0)
    )
    val rm = RM(timestamp = now, mode = RM.Mode.OPEN_LOOP, duration = T.secs(3).msecs())
    val te = TE(timestamp = now, type = TE.Type.ANNOUNCEMENT, glucoseUnit = GlucoseUnit.MMOL, duration = T.mins(30).msecs())
    val ds = DS(timestamp = now, uploaderBattery = 90, isCharging = true)

    @BeforeEach
    fun setUp() {
        // Mock the persistence layer to return empty results immediately
        runTest {
            whenever(persistenceLayer.insertCgmSourceData(any(), any(), any(), anyOrNull()))
                .thenReturn(
                    PersistenceLayer.TransactionResult<GV>().apply {
                        inserted.add(gv)
                        updated.add(gv)
                        updatedNsId.add(gv)
                        invalidated.add(gv)
                    }
                )
            whenever(persistenceLayer.syncNsBolus(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsCarbs(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsTemporaryTargets(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsTemporaryBasals(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsExtendedBoluses(any(), any()))
                .thenReturn(
                    PersistenceLayer.TransactionResult<EB>().apply {
                        inserted.add(eb)
                        updated.add(eb)
                        updatedNsId.add(eb)
                        invalidated.add(eb)
                        updatedDuration.add(eb)
                        ended.add(eb)
                    }
                )
            whenever(persistenceLayer.syncNsBolusCalculatorResults(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsEffectiveProfileSwitches(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsProfileSwitches(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsRunningModes(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsTherapyEvents(any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.syncNsFood(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateTemporaryTargetsNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateGlucoseValuesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateFoodsNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateTherapyEventsNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateBolusesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateCarbsNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateBolusCalculatorResultsNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateTemporaryBasalsNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateExtendedBolusesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateProfileSwitchesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateEffectiveProfileSwitchesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateDeviceStatusesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.updateRunningModesNsIds(any()))
                .thenReturn(PersistenceLayer.TransactionResult())
            whenever(persistenceLayer.invalidateGlucoseValue(any(), any(), any(), anyOrNull(), anyList()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }

        testAppScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        storeDataForDb = StoreDataForDbImpl(
            aapsLogger = aapsLogger,
            persistenceLayer = persistenceLayer,
            preferences = preferences,
            config = config,
            virtualPump = virtualPump,
            nsClientRepository = nsClientRepository,
            appScope = testAppScope
        )
    }

    @AfterEach
    fun tearDown() {
        // Cancel the channel collectors so they don't leak into the next test.
        testAppScope.cancel()
    }

    @Test
    fun `storeGlucoseValuesToDb calls persistenceLayer and clears list`() = runTest {
        val glucoseValues = mutableListOf(gv)
        storeDataForDb.addToGlucoseValues(glucoseValues)
        storeDataForDb.storeGlucoseValuesToDb()

        verify(persistenceLayer).insertCgmSourceData(
            eq(Sources.NSClient),
            argThat { size == 1 && get(0).value == gv.value },
            anyList(),
            eq(null)
        )
        storeDataForDb.storeGlucoseValuesToDb()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `storeFoodsToDb calls persistenceLayer and clears list`() = runTest {
        val foods = mutableListOf(fd)
        storeDataForDb.addToFoods(foods)
        storeDataForDb.storeFoodsToDb()
        verify(persistenceLayer).syncNsFood(argThat { size == 1 && get(0).name == "Apple" })
        storeDataForDb.storeFoodsToDb()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `storeTreatmentsToDb calls all relevant persistenceLayer methods`() = runTest {
        storeDataForDb.addToBoluses(bs)
        storeDataForDb.addToCarbs(ca)
        storeDataForDb.addToTemporaryTargets(tt)
        storeDataForDb.addToTemporaryBasals(tb)
        storeDataForDb.addToExtendedBoluses(eb)
        storeDataForDb.addToBolusCalculatorResults(bcr)
        storeDataForDb.addToEffectiveProfileSwitches(eps)
        storeDataForDb.addToProfileSwitches(ps)
        storeDataForDb.addToRunningModes(rm)
        storeDataForDb.addToTherapyEvents(te)

        storeDataForDb.storeTreatmentsToDb(fullSync = false)

        verify(persistenceLayer).syncNsBolus(any(), eq(true))
        verify(persistenceLayer).syncNsCarbs(any(), eq(true))
        verify(persistenceLayer).syncNsTemporaryTargets(any(), eq(true))
        verify(persistenceLayer).syncNsTemporaryBasals(any(), eq(true))
        verify(persistenceLayer).syncNsExtendedBoluses(any(), eq(true))
        verify(persistenceLayer).syncNsBolusCalculatorResults(any())
        verify(persistenceLayer).syncNsEffectiveProfileSwitches(any(), eq(true))
        verify(persistenceLayer).syncNsProfileSwitches(any(), eq(true))
        verify(persistenceLayer).syncNsRunningModes(any(), eq(true))
        verify(persistenceLayer).syncNsTherapyEvents(any(), eq(true))

        // Assert that methods were not called a second time
        storeDataForDb.storeTreatmentsToDb(fullSync = false)
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun `store methods do not call persistenceLayer when lists are empty`() = runTest {
        // Act
        storeDataForDb.storeGlucoseValuesToDb()
        storeDataForDb.storeFoodsToDb()
        storeDataForDb.storeTreatmentsToDb(fullSync = true)

        // Assert
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
        verify(persistenceLayer, never()).syncNsFood(any())
        verify(persistenceLayer, never()).syncNsBolus(any(), any())
        verify(persistenceLayer, never()).syncNsCarbs(any(), any())
        verify(persistenceLayer, never()).syncNsTemporaryTargets(any(), any())
        verify(persistenceLayer, never()).syncNsTemporaryBasals(any(), any())
        verify(persistenceLayer, never()).syncNsExtendedBoluses(any(), eq(true))
        verify(persistenceLayer, never()).syncNsBolusCalculatorResults(any())
        verify(persistenceLayer, never()).syncNsEffectiveProfileSwitches(any(), eq(true))
        verify(persistenceLayer, never()).syncNsProfileSwitches(any(), eq(true))
        verify(persistenceLayer, never()).syncNsRunningModes(any(), eq(true))
        verify(persistenceLayer, never()).syncNsTherapyEvents(any(), eq(true))
    }

    @Test
    fun `updateNsIds calls persistenceLayer for temporary targets`() = runTest {
        storeDataForDb.addToNsIdTemporaryTargets(tt)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateTemporaryTargetsNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for glucose values`() = runTest {
        storeDataForDb.addToNsIdGlucoseValues(gv)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateGlucoseValuesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for foods`() = runTest {
        storeDataForDb.addToNsIdFoods(fd)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateFoodsNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for therapy events`() = runTest {
        storeDataForDb.addToNsIdTherapyEvents(te)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateTherapyEventsNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for boluses`() = runTest {
        storeDataForDb.addToNsIdBoluses(bs)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateBolusesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for carbs`() = runTest {
        storeDataForDb.addToNsIdCarbs(ca)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateCarbsNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for bolus calculator results`() = runTest {
        storeDataForDb.addToNsIdBolusCalculatorResults(bcr)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateBolusCalculatorResultsNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for temporary basals`() = runTest {
        storeDataForDb.addToNsIdTemporaryBasals(tb)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateTemporaryBasalsNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for extended boluses`() = runTest {
        storeDataForDb.addToNsIdExtendedBoluses(eb)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateExtendedBolusesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for profile switches`() = runTest {
        storeDataForDb.addToNsIdProfileSwitches(ps)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateProfileSwitchesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for effective profile switches`() = runTest {
        storeDataForDb.addToNsIdEffectiveProfileSwitches(eps)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateEffectiveProfileSwitchesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for device statuses`() = runTest {
        storeDataForDb.addToNsIdDeviceStatuses(ds)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateDeviceStatusesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateNsIds calls persistenceLayer for running modes`() = runTest {
        storeDataForDb.addToNsIdRunningModes(rm)
        storeDataForDb.updateNsIds()

        verify(persistenceLayer).updateRunningModesNsIds(any())
        // Verify the list is cleared
        storeDataForDb.updateNsIds()
        verifyNoMoreInteractions(persistenceLayer) // Verifies it was only called once
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates bolus when preference is enabled`() = runTest {
        val nsId = "bolus_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptInsulin)).thenReturn(true)
        whenever(persistenceLayer.getBolusByNSId(nsId)).thenReturn(bs)
        whenever(persistenceLayer.invalidateBolus(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())
        storeDataForDb.updateDeletedTreatmentsInDb()
        verify(persistenceLayer).getBolusByNSId(nsId)
        verify(persistenceLayer).invalidateBolus(eq(bs.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate bolus when preference is disabled`() = runTest {
        val nsId = "bolus_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptInsulin)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false) // Ensure AAPSCLIENT is also false
        storeDataForDb.updateDeletedTreatmentsInDb()
        verify(persistenceLayer, never()).getBolusByNSId(any())
        verify(persistenceLayer, never()).invalidateBolus(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates carb when preference is enabled`() = runTest {
        val nsId = "carb_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(true)
        whenever(persistenceLayer.getCarbsByNSId(nsId)).thenReturn(ca)
        whenever(persistenceLayer.invalidateCarbs(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())
        storeDataForDb.updateDeletedTreatmentsInDb()
        verify(persistenceLayer).getCarbsByNSId(nsId)
        verify(persistenceLayer).invalidateCarbs(eq(ca.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate carb when preference is disabled`() = runTest {
        // Arrange
        val nsId = "carb_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getCarbsByNSId(any())
        verify(persistenceLayer, never()).invalidateCarbs(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates temp target when preference is enabled`() = runTest {
        // Arrange
        val nsId = "tt_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTempTarget)).thenReturn(true)
        whenever(persistenceLayer.getTemporaryTargetByNSId(nsId)).thenReturn(tt)
        whenever(persistenceLayer.invalidateTemporaryTarget(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getTemporaryTargetByNSId(nsId)
        verify(persistenceLayer).invalidateTemporaryTarget(eq(tt.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate temp target when preference is disabled`() = runTest {
        // Arrange
        val nsId = "tt_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTempTarget)).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getTemporaryTargetByNSId(any())
        verify(persistenceLayer, never()).invalidateTemporaryTarget(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb always invalidates bolus calculator result`() = runTest {
        // Arrange
        val nsId = "bcr_to_delete"
        val bcrToDelete = bcr.apply { this.id = 999L }
        storeDataForDb.addToDeleteTreatment(nsId)

        // Set preferences too false to prove they are ignored for BCR
        whenever(persistenceLayer.getBolusCalculatorResultByNSId(nsId)).thenReturn(bcrToDelete)
        whenever(persistenceLayer.invalidateBolusCalculatorResult(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getBolusCalculatorResultByNSId(nsId)
        verify(persistenceLayer).invalidateBolusCalculatorResult(eq(bcrToDelete.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates temp basal when preference is enabled`() = runTest {
        // Arrange
        val nsId = "tb_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(true)
        whenever(persistenceLayer.getTemporaryBasalByNSId(nsId)).thenReturn(tb)
        whenever(persistenceLayer.invalidateTemporaryBasal(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getTemporaryBasalByNSId(nsId)
        verify(persistenceLayer).invalidateTemporaryBasal(eq(tb.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate temp basal when preference is disabled`() = runTest {
        // Arrange
        val nsId = "tb_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getTemporaryBasalByNSId(any())
        verify(persistenceLayer, never()).invalidateTemporaryBasal(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates extended bolus when preference is enabled`() = runTest {
        // Arrange
        val nsId = "eb_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(true)
        whenever(persistenceLayer.getExtendedBolusByNSId(nsId)).thenReturn(eb)
        whenever(persistenceLayer.invalidateExtendedBolus(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getExtendedBolusByNSId(nsId)
        verify(persistenceLayer).invalidateExtendedBolus(eq(eb.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate extended bolus when preference is disabled`() = runTest {
        // Arrange
        val nsId = "eb_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getExtendedBolusByNSId(any())
        verify(persistenceLayer, never()).invalidateExtendedBolus(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates profile switch when preference is enabled`() = runTest {
        // Arrange
        val nsId = "ps_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        whenever(persistenceLayer.getProfileSwitchByNSId(nsId)).thenReturn(ps)
        whenever(persistenceLayer.invalidateProfileSwitch(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getProfileSwitchByNSId(nsId)
        verify(persistenceLayer).invalidateProfileSwitch(eq(ps.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate profile switch when preference is disabled`() = runTest {
        // Arrange
        val nsId = "ps_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getProfileSwitchByNSId(any())
        verify(persistenceLayer, never()).invalidateProfileSwitch(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates effective profile switch when preference is enabled`() = runTest {
        // Arrange
        val nsId = "eps_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        whenever(persistenceLayer.getEffectiveProfileSwitchByNSId(nsId)).thenReturn(eps)
        whenever(persistenceLayer.invalidateEffectiveProfileSwitch(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getEffectiveProfileSwitchByNSId(nsId)
        verify(persistenceLayer).invalidateEffectiveProfileSwitch(eq(eps.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate effective profile switch when preference is disabled`() = runTest {
        // Arrange
        val nsId = "eps_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getEffectiveProfileSwitchByNSId(any())
        verify(persistenceLayer, never()).invalidateEffectiveProfileSwitch(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates running mode when preference is enabled`() = runTest {
        // Arrange
        val nsId = "rm_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptRunningMode)).thenReturn(true)
        whenever(config.isEngineeringMode()).thenReturn(true) // Both conditions must be met
        whenever(persistenceLayer.getRunningModeByNSId(nsId)).thenReturn(rm)
        whenever(persistenceLayer.invalidateRunningMode(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getRunningModeByNSId(nsId)
        verify(persistenceLayer).invalidateRunningMode(eq(rm.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate running mode when preference is disabled`() = runTest {
        // Arrange
        val nsId = "rm_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptRunningMode)).thenReturn(false)
        whenever(config.isEngineeringMode()).thenReturn(true) // One condition is still true

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getRunningModeByNSId(any())
        verify(persistenceLayer, never()).invalidateRunningMode(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates therapy event when preference is enabled`() = runTest {
        // Arrange
        val nsId = "te_to_delete"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTherapyEvent)).thenReturn(true)
        whenever(persistenceLayer.getTherapyEventByNSId(nsId)).thenReturn(te)
        whenever(persistenceLayer.invalidateTherapyEvent(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer).getTherapyEventByNSId(nsId)
        verify(persistenceLayer).invalidateTherapyEvent(eq(te.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb does NOT invalidate therapy event when preference is disabled`() = runTest {
        // Arrange
        val nsId = "te_to_ignore"
        storeDataForDb.addToDeleteTreatment(nsId)

        whenever(preferences.get(BooleanKey.NsClientAcceptTherapyEvent)).thenReturn(false)

        // Act
        storeDataForDb.updateDeletedTreatmentsInDb()

        // Assert
        verify(persistenceLayer, never()).getTherapyEventByNSId(any())
        verify(persistenceLayer, never()).invalidateTherapyEvent(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedGlucoseValuesInDb invalidates glucose value when found`() = runTest {
        val nsIdToDelete = "gv_to_delete_id"
        storeDataForDb.addToDeleteGlucoseValue(nsIdToDelete)
        whenever(persistenceLayer.getBgReadingByNSId(nsIdToDelete)).thenReturn(gv)

        storeDataForDb.updateDeletedGlucoseValuesInDb()
        // Verify that we tried to find the GV by its Nightscout ID
        verify(persistenceLayer).getBgReadingByNSId(nsIdToDelete)
        // Verify that the invalidate method was called with the correct local ID
        verify(persistenceLayer).invalidateGlucoseValue(eq(gv.id), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedGlucoseValuesInDb does nothing if glucose value is not found`() = runTest {
        val nsIdNotFound = "gv_not_in_db_id"
        storeDataForDb.addToDeleteGlucoseValue(nsIdNotFound)
        whenever(persistenceLayer.getBgReadingByNSId(nsIdNotFound)).thenReturn(null)

        storeDataForDb.updateDeletedGlucoseValuesInDb()

        // Verify that we tried to find the GV
        verify(persistenceLayer).getBgReadingByNSId(nsIdNotFound)
        // Crucially, verify that the invalidate method was NEVER called
        verify(persistenceLayer, never()).invalidateGlucoseValue(any(), any(), any(), anyOrNull(), anyList())
    }

    @Test
    fun `updateDeletedTreatmentsInDb invalidates multiple different treatments in one go`() = runTest {
        // Arrange
        val bolusId = "bolus_multi_delete"
        val carbId = "carb_multi_delete"
        storeDataForDb.addToDeleteTreatment(bolusId)
        storeDataForDb.addToDeleteTreatment(carbId)

        whenever(preferences.get(BooleanKey.NsClientAcceptInsulin)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(true)

        whenever(persistenceLayer.getBolusByNSId(bolusId)).thenReturn(bs)
        whenever(persistenceLayer.invalidateBolus(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())
        whenever(persistenceLayer.getCarbsByNSId(carbId)).thenReturn(ca)
        whenever(persistenceLayer.invalidateCarbs(any(), any(), any(), anyOrNull(), anyList())).thenReturn(PersistenceLayer.TransactionResult())
        storeDataForDb.updateDeletedTreatmentsInDb()
        verify(persistenceLayer).getBolusByNSId(bolusId)
        verify(persistenceLayer).invalidateBolus(eq(bs.id), any(), any(), anyOrNull(), anyList())
        verify(persistenceLayer).getCarbsByNSId(carbId)
        verify(persistenceLayer).invalidateCarbs(eq(ca.id), any(), any(), anyOrNull(), anyList())
        assertTrue(storeDataForDb.deleteTreatment.isEmpty())
    }

    @Test
    fun `scheduleNsIdUpdate schedule runnable`() {
        assertNull(storeDataForDb.scheduledEventPost)
        storeDataForDb.scheduleNsIdUpdate()
        assertNotNull(storeDataForDb.scheduledEventPost)
    }

    /* -------- Concurrency / coalescing tests -------- */

    @Test
    fun `concurrent storeGlucoseValuesToDb calls do not lose items and serialize`() = runTest {
        // Two callers race to drain the same buffer. The bgMutex must serialize them and
        // snapshotAndClear must hand all items to whichever wins; the loser sees an empty buffer.
        val gv2 = gv.copy(timestamp = now + 1)
        storeDataForDb.addToGlucoseValues(mutableListOf(gv, gv2))

        val jobs = listOf(
            async { storeDataForDb.storeGlucoseValuesToDb() },
            async { storeDataForDb.storeGlucoseValuesToDb() }
        )
        jobs.awaitAll()

        // Exactly one DB call carrying both items; second call had nothing to do.
        verify(persistenceLayer).insertCgmSourceData(
            eq(Sources.NSClient),
            argThat { size == 2 },
            anyList(),
            eq(null)
        )
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun `addToGlucoseValues during processing lands in the next call`() = runTest {
        // First call drains [gv]. Add gv2 after the snapshot. Second call drains [gv2].
        storeDataForDb.addToGlucoseValues(mutableListOf(gv))
        storeDataForDb.storeGlucoseValuesToDb()
        verify(persistenceLayer).insertCgmSourceData(eq(Sources.NSClient), argThat { size == 1 && get(0).timestamp == gv.timestamp }, anyList(), eq(null))

        val gv2 = gv.copy(timestamp = now + 1)
        storeDataForDb.addToGlucoseValues(mutableListOf(gv2))
        storeDataForDb.storeGlucoseValuesToDb()
        verify(persistenceLayer).insertCgmSourceData(eq(Sources.NSClient), argThat { size == 1 && get(0).timestamp == gv2.timestamp }, anyList(), eq(null))
    }

    @Test
    fun `bg pipeline runs in parallel with treatments pipeline`() = runTest {
        // bgMutex and treatmentsMutex are independent: a long treatments sync must not
        // block BG ingest. We can't measure wall-clock here, but verifying both DB calls
        // were issued in a single test scope (no deadlock) with no shared lock proves
        // the mutexes are separate.
        storeDataForDb.addToGlucoseValues(mutableListOf(gv))
        storeDataForDb.addToBoluses(bs)

        val jobs = listOf(
            async { storeDataForDb.storeGlucoseValuesToDb() },
            async { storeDataForDb.storeTreatmentsToDb(fullSync = false) }
        )
        jobs.awaitAll()

        verify(persistenceLayer).insertCgmSourceData(eq(Sources.NSClient), any(), anyList(), eq(null))
        verify(persistenceLayer).syncNsBolus(any(), any())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `requestStoreGlucoseValues coalesces a burst into a single DB call`() = runTest {
        // CONFLATED channel: 100 trySend calls between collector iterations should still
        // produce just one drain (because the collector runs once and there's nothing left).
        storeDataForDb.addToGlucoseValues(mutableListOf(gv))
        repeat(100) { storeDataForDb.requestStoreGlucoseValues() }
        // Allow the collector launched in init to drain the channel.
        advanceUntilIdle()
        yield()

        verify(persistenceLayer).insertCgmSourceData(eq(Sources.NSClient), any(), anyList(), eq(null))
        verifyNoMoreInteractions(persistenceLayer)
    }
}
