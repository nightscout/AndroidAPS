package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.BooleanKey
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DataSyncSelectorV3Test : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var nsClient: NsClient

    private lateinit var storeDataForDb: StoreDataForDb
    private lateinit var sut: DataSyncSelectorV3

    @BeforeEach
    fun setUp() {
        storeDataForDb = StoreDataForDbImpl(aapsLogger, rxBus, persistenceLayer, preferences, config, nsClientSource, virtualPump)
        sut = DataSyncSelectorV3(preferences, aapsLogger, dateUtil, profileFunction, activePlugin, persistenceLayer, rxBus, storeDataForDb, config)
    }

    @Test
    fun bgUploadEnabledTest() {

        class NSClientSourcePlugin() : NSClientSource, BgSource {

            override fun isEnabled(): Boolean = true
            override fun detectSource(glucoseValue: GV) {}
        }
        val nsClientSourcePlugin = NSClientSourcePlugin()

        class AnotherSourcePlugin() : BgSource
        val anotherSourcePlugin = AnotherSourcePlugin()

        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(false)
        whenever(activePlugin.activeBgSource).thenReturn(nsClientSourcePlugin)
        assertThat(sut.bgUploadEnabled).isFalse()

        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(true)
        whenever(activePlugin.activeBgSource).thenReturn(nsClientSourcePlugin)
        assertThat(sut.bgUploadEnabled).isFalse()

        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(true)
        whenever(activePlugin.activeBgSource).thenReturn(anotherSourcePlugin)
        assertThat(sut.bgUploadEnabled).isTrue()
    }

    @Test
    fun resetToNextFullSyncTest() {
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(1)
        sut.resetToNextFullSync()
        verify(preferences, Times(1)).remove(NsclientLongKey.GlucoseValueLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.TemporaryBasalLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.TemporaryTargetLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.ExtendedBolusLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.FoodLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.BolusLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.CarbsLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.BolusCalculatorLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.TherapyEventLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.ProfileSwitchLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.RunningModeLastSyncedId)
        verify(preferences, Times(1)).remove(NsclientLongKey.ProfileStoreLastSyncedId)
        verify(preferences, Times(1)).put(NsclientLongKey.DeviceStatusLastSyncedId, 1)

        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(null)
        sut.resetToNextFullSync()
        verify(preferences, Times(1)).remove(NsclientLongKey.DeviceStatusLastSyncedId)
    }

    @Test
    fun confirmLastTest() {
        // Bolus
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(2)
        sut.confirmLastBolusIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(1)
        sut.confirmLastBolusIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.BolusLastSyncedId, 2)
        // Carbs
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(2)
        sut.confirmLastCarbsIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.CarbsLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(1)
        sut.confirmLastCarbsIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.CarbsLastSyncedId, 2)
        // BolusCalculatorResults
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(2)
        sut.confirmLastBolusCalculatorResultsIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusCalculatorLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(1)
        sut.confirmLastBolusCalculatorResultsIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.BolusCalculatorLastSyncedId, 2)
        // TempTargets
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(2)
        sut.confirmLastTempTargetsIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.TemporaryTargetLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(1)
        sut.confirmLastTempTargetsIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.TemporaryTargetLastSyncedId, 2)
// NSCv3 doesn't support food update
/*
        // Food
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(2)
        sut.confirmLastFoodIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.FoodLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(1)
        sut.confirmLastFoodIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.FoodLastSyncedId, 2)
 */
        // GlucoseValue
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(2)
        sut.confirmLastGlucoseValueIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.GlucoseValueLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(1)
        sut.confirmLastGlucoseValueIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.GlucoseValueLastSyncedId, 2)
        // TherapyEvent
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(2)
        sut.confirmLastTherapyEventIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.TherapyEventLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(1)
        sut.confirmLastTherapyEventIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.TherapyEventLastSyncedId, 2)
        // DeviceStatus
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(2)
        sut.confirmLastDeviceStatusIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.DeviceStatusLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(1)
        sut.confirmLastDeviceStatusIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.DeviceStatusLastSyncedId, 2)
        // TemporaryBasal
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(2)
        sut.confirmLastTemporaryBasalIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.TemporaryBasalLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(1)
        sut.confirmLastTemporaryBasalIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.TemporaryBasalLastSyncedId, 2)
        // ExtendedBolus
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(2)
        sut.confirmLastExtendedBolusIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.ExtendedBolusLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(1)
        sut.confirmLastExtendedBolusIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.ExtendedBolusLastSyncedId, 2)
        // ProfileSwitch
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(2)
        sut.confirmLastProfileSwitchIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.ProfileSwitchLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(1)
        sut.confirmLastProfileSwitchIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileSwitchLastSyncedId, 2)
        // EffectiveProfileSwitch
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(2)
        sut.confirmLastEffectiveProfileSwitchIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(1)
        sut.confirmLastEffectiveProfileSwitchIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, 2)
        // OfflineEvent
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(2)
        sut.confirmLastRunningModeIdIfGreater(2)
        verify(preferences, Times(0)).put(NsclientLongKey.RunningModeLastSyncedId, 2)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(1)
        sut.confirmLastRunningModeIdIfGreater(2)
        verify(preferences, Times(1)).put(NsclientLongKey.RunningModeLastSyncedId, 2)
        // ProfileStore
        sut.confirmLastProfileStore(2)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileStoreLastSyncedId, 2)
    }

    @Test
    fun processChangedBolusesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastBolusId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementBolus(0)).thenReturn(Maybe.empty())
        sut.processChangedBoluses()
        verify(preferences, Times(1)).put(NsclientLongKey.BolusLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        clearInvocations(preferences, activePlugin)
    }

    @Test
    fun processChangedBolusesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)

        sut.processChangedBoluses()

        // Should not call getNextSyncElementBolus when paused
        verify(persistenceLayer, Times(0)).getNextSyncElementBolus(any())
        Unit
    }

    @Test
    fun processChangedBolusesWithEmptyQueueTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(5L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)
        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.empty())

        sut.processChangedBoluses()

        // Should call getNextSyncElementBolus once and then stop
        verify(persistenceLayer, Times(1)).getNextSyncElementBolus(5L)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    @Test
    fun queueSizeTest() {
        // All counters initialized to -1, so total should be -13 (13 fields)
        assertThat(sut.queueSize()).isEqualTo(-12)
    }

    @Test
    fun doUploadWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)

        sut.doUpload()

        // Should not calculate queue counters when paused
        verify(persistenceLayer, Times(0)).getLastBolusId()
        verify(persistenceLayer, Times(0)).getLastCarbsId()
        Unit
    }

    @Test
    fun doUploadWhenUploadDisabledTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(false)

        sut.doUpload()

        // Should not process when upload is disabled
        verify(persistenceLayer, Times(0)).getLastBolusId()
        Unit
    }

    @Test
    fun doUploadCalculatesQueueCountersTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)

        // Mock all the getLastId methods
        whenever(persistenceLayer.getLastBolusId()).thenReturn(100L)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(200L)
        whenever(persistenceLayer.getLastBolusCalculatorResultId()).thenReturn(50L)
        whenever(persistenceLayer.getLastTemporaryTargetId()).thenReturn(75L)
        //whenever(persistenceLayer.getLastFoodId()).thenReturn(25L)
        whenever(persistenceLayer.getLastGlucoseValueId()).thenReturn(500L)
        whenever(persistenceLayer.getLastTherapyEventId()).thenReturn(30L)
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(10L)
        whenever(persistenceLayer.getLastTemporaryBasalId()).thenReturn(40L)
        whenever(persistenceLayer.getLastExtendedBolusId()).thenReturn(20L)
        whenever(persistenceLayer.getLastProfileSwitchId()).thenReturn(15L)
        whenever(persistenceLayer.getLastEffectiveProfileSwitchId()).thenReturn(60L)
        whenever(persistenceLayer.getLastRunningModeId()).thenReturn(5L)

        // Mock all the sync preferences to 0
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(0L)
        //whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(0L)

        // Mock all the getNextSyncElement methods to return empty
        whenever(persistenceLayer.getNextSyncElementBolus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementCarbs(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementBolusCalculatorResult(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryTarget(0)).thenReturn(Maybe.empty())
        //whenever(persistenceLayer.getNextSyncElementFood(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementGlucoseValue(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTherapyEvent(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementDeviceStatus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryBasal(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementExtendedBolus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementEffectiveProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementRunningMode(0)).thenReturn(Maybe.empty())

        sut.doUpload()

        // Queue counters should be calculated
        assertThat(sut.queueSize()).isEqualTo(1105L) // Sum of all differences
    }

    @Test
    fun doUploadWithPartialSyncTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUploadData)).thenReturn(true)

        whenever(persistenceLayer.getLastBolusId()).thenReturn(100L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(50L)

        // Mock other IDs to 0 or empty
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(0L)
        whenever(persistenceLayer.getLastBolusCalculatorResultId()).thenReturn(0L)
        whenever(persistenceLayer.getLastTemporaryTargetId()).thenReturn(0L)
        whenever(persistenceLayer.getLastFoodId()).thenReturn(0L)
        whenever(persistenceLayer.getLastGlucoseValueId()).thenReturn(0L)
        whenever(persistenceLayer.getLastTherapyEventId()).thenReturn(0L)
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(0L)
        whenever(persistenceLayer.getLastTemporaryBasalId()).thenReturn(0L)
        whenever(persistenceLayer.getLastExtendedBolusId()).thenReturn(0L)
        whenever(persistenceLayer.getLastProfileSwitchId()).thenReturn(0L)
        whenever(persistenceLayer.getLastEffectiveProfileSwitchId()).thenReturn(0L)
        whenever(persistenceLayer.getLastRunningModeId()).thenReturn(0L)

        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(0L)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(0L)

        whenever(persistenceLayer.getNextSyncElementBolus(50)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementCarbs(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementBolusCalculatorResult(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryTarget(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementFood(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementGlucoseValue(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTherapyEvent(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementDeviceStatus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementTemporaryBasal(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementExtendedBolus(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementEffectiveProfileSwitch(0)).thenReturn(Maybe.empty())
        whenever(persistenceLayer.getNextSyncElementRunningMode(0)).thenReturn(Maybe.empty())

        sut.doUpload()

        // Only boluses should have remaining items (100 - 50 = 50)
        assertThat(sut.queueSize()).isEqualTo(50L)
    }

    @Test
    fun bgUploadEnabledWhenBothConditionsFalseTest() {
        whenever(preferences.get(BooleanKey.BgSourceUploadToNs)).thenReturn(false)
        val nonNsClientSource = object : BgSource {}
        whenever(activePlugin.activeBgSource).thenReturn(nonNsClientSource)

        assertThat(sut.bgUploadEnabled).isFalse()
    }

    @Test
    fun confirmMethodsDoNotUpdateWhenIdIsEqualTest() {
        // Test that confirm methods don't update when new ID equals current ID
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)
        sut.confirmLastBolusIdIfGreater(5)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 5)

        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(10L)
        sut.confirmLastCarbsIdIfGreater(10)
        verify(preferences, Times(0)).put(NsclientLongKey.CarbsLastSyncedId, 10)
    }

    @Test
    fun confirmMethodsDoNotUpdateWhenIdIsLessTest() {
        // Test that confirm methods don't update when new ID is less than current ID
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(10L)
        sut.confirmLastBolusIdIfGreater(5)
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 5)

        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(100L)
        sut.confirmLastGlucoseValueIdIfGreater(50)
        verify(preferences, Times(0)).put(NsclientLongKey.GlucoseValueLastSyncedId, 50)
    }

    @Test
    fun confirmProfileStoreAlwaysUpdatesTest() {
        // ProfileStore always updates regardless of current value
        sut.confirmLastProfileStore(42)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileStoreLastSyncedId, 42)

        sut.confirmLastProfileStore(10)
        verify(preferences, Times(1)).put(NsclientLongKey.ProfileStoreLastSyncedId, 10)
    }

    // Tests for processChangedCarbs
    @Test
    fun processChangedCarbsWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(5L)

        sut.processChangedCarbs()

        verify(persistenceLayer, Times(0)).getNextSyncElementCarbs(any())
        Unit
    }

    @Test
    fun processChangedCarbsAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementCarbs(0)).thenReturn(Maybe.empty())

        sut.processChangedCarbs()

        verify(preferences, Times(1)).put(NsclientLongKey.CarbsLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedBolusCalculatorResults
    @Test
    fun processChangedBolusCalculatorResultsWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastBolusCalculatorResultId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(5L)

        sut.processChangedBolusCalculatorResults()

        verify(persistenceLayer, Times(0)).getNextSyncElementBolusCalculatorResult(any())
        Unit
    }

    @Test
    fun processChangedBolusCalculatorResultsAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastBolusCalculatorResultId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementBolusCalculatorResult(0)).thenReturn(Maybe.empty())

        sut.processChangedBolusCalculatorResults()

        verify(preferences, Times(1)).put(NsclientLongKey.BolusCalculatorLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedTempTargets
    @Test
    fun processChangedTempTargetsWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastTemporaryTargetId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(5L)

        sut.processChangedTempTargets()

        verify(persistenceLayer, Times(0)).getNextSyncElementTemporaryTarget(any())
        Unit
    }

    @Test
    fun processChangedTempTargetsAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastTemporaryTargetId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementTemporaryTarget(0)).thenReturn(Maybe.empty())

        sut.processChangedTempTargets()

        verify(preferences, Times(1)).put(NsclientLongKey.TemporaryTargetLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedFoods
/*
// NSCv3 doesn't support food update
    @Test
    fun processChangedFoodsWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastFoodId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(5L)

        sut.processChangedFoods()

        verify(persistenceLayer, Times(0)).getNextSyncElementFood(any())
        Unit
    }

    @Test
    fun processChangedFoodsAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastFoodId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.FoodLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementFood(0)).thenReturn(Maybe.empty())

        sut.processChangedFoods()

        verify(preferences, Times(1)).put(NsclientLongKey.FoodLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedGlucoseValues
    @Test
    fun processChangedGlucoseValuesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastGlucoseValueId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(5L)

        sut.processChangedGlucoseValues()

        verify(persistenceLayer, Times(0)).getNextSyncElementGlucoseValue(any())
        Unit
    }
*/
    @Test
    fun processChangedGlucoseValuesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastGlucoseValueId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementGlucoseValue(0)).thenReturn(Maybe.empty())

        sut.processChangedGlucoseValues()

        verify(preferences, Times(1)).put(NsclientLongKey.GlucoseValueLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedTherapyEvents
    @Test
    fun processChangedTherapyEventsWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastTherapyEventId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(5L)

        sut.processChangedTherapyEvents()

        verify(persistenceLayer, Times(0)).getNextSyncElementTherapyEvent(any())
        Unit
    }

    @Test
    fun processChangedTherapyEventsAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastTherapyEventId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.TherapyEventLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementTherapyEvent(0)).thenReturn(Maybe.empty())

        sut.processChangedTherapyEvents()

        verify(preferences, Times(1)).put(NsclientLongKey.TherapyEventLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedDeviceStatuses
    @Test
    fun processChangedDeviceStatusesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(5L)

        sut.processChangedDeviceStatuses()

        verify(persistenceLayer, Times(0)).getNextSyncElementDeviceStatus(any())
        Unit
    }

    @Test
    fun processChangedDeviceStatusesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastDeviceStatusId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementDeviceStatus(0)).thenReturn(Maybe.empty())

        sut.processChangedDeviceStatuses()

        verify(preferences, Times(1)).put(NsclientLongKey.DeviceStatusLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedTemporaryBasals
    @Test
    fun processChangedTemporaryBasalsWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastTemporaryBasalId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(5L)

        sut.processChangedTemporaryBasals()

        verify(persistenceLayer, Times(0)).getNextSyncElementTemporaryBasal(any())
        Unit
    }

    @Test
    fun processChangedTemporaryBasalsAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastTemporaryBasalId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementTemporaryBasal(0)).thenReturn(Maybe.empty())

        sut.processChangedTemporaryBasals()

        verify(preferences, Times(1)).put(NsclientLongKey.TemporaryBasalLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedExtendedBoluses
    @Test
    fun processChangedExtendedBolusesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastExtendedBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(5L)

        sut.processChangedExtendedBoluses()

        verify(persistenceLayer, Times(0)).getNextSyncElementExtendedBolus(any())
        Unit
    }

    @Test
    fun processChangedExtendedBolusesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastExtendedBolusId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementExtendedBolus(0)).thenReturn(Maybe.empty())

        sut.processChangedExtendedBoluses()

        verify(preferences, Times(1)).put(NsclientLongKey.ExtendedBolusLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedProfileSwitches
    @Test
    fun processChangedProfileSwitchesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastProfileSwitchId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(5L)

        sut.processChangedProfileSwitches()

        verify(persistenceLayer, Times(0)).getNextSyncElementProfileSwitch(any())
        Unit
    }

    @Test
    fun processChangedProfileSwitchesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastProfileSwitchId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementProfileSwitch(0)).thenReturn(Maybe.empty())

        sut.processChangedProfileSwitches()

        verify(preferences, Times(1)).put(NsclientLongKey.ProfileSwitchLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedEffectiveProfileSwitches
    @Test
    fun processChangedEffectiveProfileSwitchesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastEffectiveProfileSwitchId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(5L)

        sut.processChangedEffectiveProfileSwitches()

        verify(persistenceLayer, Times(0)).getNextSyncElementEffectiveProfileSwitch(any())
        Unit
    }

    @Test
    fun processChangedEffectiveProfileSwitchesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastEffectiveProfileSwitchId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementEffectiveProfileSwitch(0)).thenReturn(Maybe.empty())

        sut.processChangedEffectiveProfileSwitches()

        verify(preferences, Times(1)).put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Tests for processChangedRunningModes
    @Test
    fun processChangedRunningModesWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(persistenceLayer.getLastRunningModeId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(5L)

        sut.processChangedRunningModes()

        verify(persistenceLayer, Times(0)).getNextSyncElementRunningMode(any())
        Unit
    }

    @Test
    fun processChangedRunningModesAfterDbResetTest() = runBlocking {
        whenever(persistenceLayer.getLastRunningModeId()).thenReturn(0)
        whenever(preferences.get(NsclientLongKey.RunningModeLastSyncedId)).thenReturn(1)
        whenever(persistenceLayer.getNextSyncElementRunningMode(0)).thenReturn(Maybe.empty())

        sut.processChangedRunningModes()

        verify(preferences, Times(1)).put(NsclientLongKey.RunningModeLastSyncedId, 0)
        verify(activePlugin, Times(0)).activeNsClient
        Unit
    }

    // Test for processChangedProfileStore
    @Test
    fun processChangedProfileStoreWhenPausedTest() = runBlocking {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)

        sut.processChangedProfileStore()

        verify(activePlugin, Times(0)).activeNsClient?.nsAdd(any(), any(), any())
        Unit
    }

    // Tests for processChangedBoluses with getNextSyncElement returning data

    @Test
    fun processChangedBolusesWithNewBolusTest() = runBlocking {
        // Setup: new bolus without NS id (should call nsAdd)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        // Return 5L first, then 6L after preference is updated
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L, 6L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        // Create test bolus without nightscoutId
        val bolus = BS(
            id = 6,
            timestamp = 1000L,
            amount = 5.0,
            type = BS.Type.NORMAL,
            ids = IDs()
        )
        val pair = Pair(bolus, bolus)

        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.just(pair))
        whenever(persistenceLayer.getNextSyncElementBolus(6L)).thenReturn(Maybe.empty())
        whenever(nsClient.nsAdd(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())).thenReturn(true)

        sut.processChangedBoluses()

        // Verify nsAdd was called
        verify(nsClient, Times(1)).nsAdd(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        Unit
    }

    @Test
    fun processChangedBolusesWithExistingBolusTest() = runBlocking {
        // Setup: existing bolus with NS id and changes (should call nsUpdate)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        // Return 5L first iteration, then 5L again after update (since we update with old id)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L, 5L, 5L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        // Create modified bolus with nightscoutId
        val oldBolus = BS(
            id = 5,
            timestamp = 1000L,
            amount = 5.0,
            type = BS.Type.NORMAL,
            ids = IDs(nightscoutId = "ns123")
        )
        val newBolus = BS(
            id = 6,
            timestamp = 1000L,
            amount = 5.5, // Modified amount
            type = BS.Type.NORMAL,
            ids = IDs(nightscoutId = "ns123")
        )
        val pair = Pair(newBolus, oldBolus)

        whenever(persistenceLayer.getNextSyncElementBolus(5L))
            .thenReturn(Maybe.just(pair))
            .thenReturn(Maybe.empty())
        whenever(nsClient.nsUpdate(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())).thenReturn(true)

        sut.processChangedBoluses()

        // Verify nsUpdate was called
        verify(nsClient, Times(1)).nsUpdate(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        Unit
    }

    @Test
    fun processChangedBolusesWithBolusFromNSTest() = runBlocking {
        // Setup: bolus loaded from NS (should ignore)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L, 6L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        // Create bolus with same id and existing nightscoutId (loaded from NS)
        val bolus = BS(
            id = 6,
            timestamp = 1000L,
            amount = 5.0,
            type = BS.Type.NORMAL,
            ids = IDs(nightscoutId = "ns123")
        )
        val pair = Pair(bolus, bolus)

        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.just(pair))
        whenever(persistenceLayer.getNextSyncElementBolus(6L)).thenReturn(Maybe.empty())

        sut.processChangedBoluses()

        // Verify no nsAdd or nsUpdate was called (ignored)
        verify(nsClient, Times(0)).nsAdd(any(), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        verify(nsClient, Times(0)).nsUpdate(any(), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        Unit
    }

    @Test
    fun processChangedBolusesWithOnlyNsIdChangedTest() = runBlocking {
        // Setup: bolus with only NS id changed (should ignore)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L, 6L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        // Create bolus where only nightscoutId was added
        val oldBolus = BS(
            id = 5,
            timestamp = 1000L,
            amount = 5.0,
            type = BS.Type.NORMAL,
            ids = IDs()
        )
        val newBolus = BS(
            id = 6,
            timestamp = 1000L,
            amount = 5.0,
            type = BS.Type.NORMAL,
            ids = IDs(nightscoutId = "ns123")
        )
        val pair = Pair(newBolus, oldBolus)

        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.just(pair))
        whenever(persistenceLayer.getNextSyncElementBolus(6L)).thenReturn(Maybe.empty())

        sut.processChangedBoluses()

        // Verify no nsAdd or nsUpdate was called (only NS id changed)
        verify(nsClient, Times(0)).nsAdd(any(), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        verify(nsClient, Times(0)).nsUpdate(any(), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        Unit
    }

    @Test
    fun processChangedBolusesWhenNsAddFailsTest() = runBlocking {
        // Setup: nsAdd returns false (should stop loop)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        val bolus = BS(
            id = 6,
            timestamp = 1000L,
            amount = 5.0,
            type = BS.Type.NORMAL,
            ids = IDs()
        )
        val pair = Pair(bolus, bolus)

        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.just(pair))
        whenever(nsClient.nsAdd(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())).thenReturn(false)

        sut.processChangedBoluses()

        // Verify nsAdd was called but loop stopped (no confirmLastId call)
        verify(nsClient, Times(1)).nsAdd(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        verify(preferences, Times(0)).put(NsclientLongKey.BolusLastSyncedId, 6L)
    }

    @Test
    fun processChangedBolusesWithMultipleBolusesTest() = runBlocking {
        // Setup: multiple boluses to sync
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastBolusId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.BolusLastSyncedId)).thenReturn(5L, 5L, 6L, 6L, 7L, 7L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        val bolus1 = BS(id = 6, timestamp = 1000L, amount = 5.0, type = BS.Type.NORMAL, ids = IDs())
        val bolus2 = BS(id = 7, timestamp = 2000L, amount = 3.0, type = BS.Type.NORMAL, ids = IDs())

        whenever(persistenceLayer.getNextSyncElementBolus(5L)).thenReturn(Maybe.just(Pair(bolus1, bolus1)))
        whenever(persistenceLayer.getNextSyncElementBolus(6L)).thenReturn(Maybe.just(Pair(bolus2, bolus2)))
        whenever(persistenceLayer.getNextSyncElementBolus(7L)).thenReturn(Maybe.empty())
        whenever(nsClient.nsAdd(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())).thenReturn(true)

        sut.processChangedBoluses()

        // Verify both boluses were synced
        verify(nsClient, Times(2)).nsAdd(eq("treatments"), any<DataSyncSelector.PairBolus>(), any(), anyOrNull())
        Unit
    }

    // Tests for processChangedCarbs with getNextSyncElement returning data

    @Test
    fun processChangedCarbsWithNewCarbsTest() = runBlocking {
        // Setup: new carbs without NS id (should call nsAdd)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(5L, 6L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        val carbs = CA(
            id = 6,
            timestamp = 1000L,
            amount = 30.0,
            duration = 0L,
            ids = IDs()
        )
        val pair = Pair(carbs, carbs)

        whenever(persistenceLayer.getNextSyncElementCarbs(5L)).thenReturn(Maybe.just(pair))
        whenever(persistenceLayer.getNextSyncElementCarbs(6L)).thenReturn(Maybe.empty())
        whenever(nsClient.nsAdd(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())).thenReturn(true)

        sut.processChangedCarbs()

        // Verify nsAdd was called
        verify(nsClient, Times(1)).nsAdd(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())
        Unit
    }

    @Test
    fun processChangedCarbsWithExistingCarbsTest() = runBlocking {
        // Setup: existing carbs with NS id and changes (should call nsUpdate)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(5L, 5L, 5L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        val oldCarbs = CA(
            id = 5,
            timestamp = 1000L,
            amount = 30.0,
            duration = 0L,
            ids = IDs(nightscoutId = "ns123")
        )
        val newCarbs = CA(
            id = 6,
            timestamp = 1000L,
            amount = 35.0, // Modified amount
            duration = 0L,
            ids = IDs(nightscoutId = "ns123")
        )
        val pair = Pair(newCarbs, oldCarbs)

        whenever(persistenceLayer.getNextSyncElementCarbs(5L))
            .thenReturn(Maybe.just(pair))
            .thenReturn(Maybe.empty())
        whenever(nsClient.nsUpdate(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())).thenReturn(true)

        sut.processChangedCarbs()

        // Verify nsUpdate was called
        verify(nsClient, Times(1)).nsUpdate(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())
        Unit
    }

    @Test
    fun processChangedCarbsWhenNsUpdateFailsTest() = runBlocking {
        // Setup: nsUpdate returns false (should stop loop)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(5L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        val oldCarbs = CA(
            id = 5,
            timestamp = 1000L,
            amount = 30.0,
            duration = 0L,
            ids = IDs(nightscoutId = "ns123")
        )
        val newCarbs = CA(
            id = 6,
            timestamp = 1000L,
            amount = 35.0,
            duration = 0L,
            ids = IDs(nightscoutId = "ns123")
        )
        val pair = Pair(newCarbs, oldCarbs)

        whenever(persistenceLayer.getNextSyncElementCarbs(5L)).thenReturn(Maybe.just(pair))
        whenever(nsClient.nsUpdate(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())).thenReturn(false)

        sut.processChangedCarbs()

        // Verify nsUpdate was called but loop stopped (no confirmLastId call)
        verify(nsClient, Times(1)).nsUpdate(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())
        verify(preferences, Times(0)).put(NsclientLongKey.CarbsLastSyncedId, 6L)
    }

    @Test
    fun processChangedCarbsWithMultipleCarbsTest() = runBlocking {
        // Setup: multiple carbs entries to sync
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(persistenceLayer.getLastCarbsId()).thenReturn(10L)
        whenever(preferences.get(NsclientLongKey.CarbsLastSyncedId)).thenReturn(5L, 5L,6L, 6L, 7L, 7L, 8L, 8L)
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)

        val carbs1 = CA(id = 6, timestamp = 1000L, amount = 30.0, duration = 0L, ids = IDs())
        val carbs2 = CA(id = 7, timestamp = 2000L, amount = 20.0, duration = 0L, ids = IDs())
        val carbs3 = CA(id = 8, timestamp = 3000L, amount = 15.0, duration = 0L, ids = IDs())

        whenever(persistenceLayer.getNextSyncElementCarbs(5L)).thenReturn(Maybe.just(Pair(carbs1, carbs1)))
        whenever(persistenceLayer.getNextSyncElementCarbs(6L)).thenReturn(Maybe.just(Pair(carbs2, carbs2)))
        whenever(persistenceLayer.getNextSyncElementCarbs(7L)).thenReturn(Maybe.just(Pair(carbs3, carbs3)))
        whenever(persistenceLayer.getNextSyncElementCarbs(8L)).thenReturn(Maybe.empty())
        whenever(nsClient.nsAdd(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())).thenReturn(true)

        sut.processChangedCarbs()

        // Verify all carbs were synced
        verify(nsClient, Times(3)).nsAdd(eq("treatments"), any<DataSyncSelector.PairCarbs>(), any(), anyOrNull())
        verify(preferences, Times(1)).put(NsclientLongKey.CarbsLastSyncedId, 6L)
        verify(preferences, Times(1)).put(NsclientLongKey.CarbsLastSyncedId, 7L)
        verify(preferences, Times(1)).put(NsclientLongKey.CarbsLastSyncedId, 8L)
    }
}