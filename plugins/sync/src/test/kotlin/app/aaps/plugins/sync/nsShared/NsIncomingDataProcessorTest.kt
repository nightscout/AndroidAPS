package app.aaps.plugins.sync.nsShared

import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.localmodel.treatment.NSEffectiveProfileSwitch
import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryTarget
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.shared.tests.TestBaseWithProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NsIncomingDataProcessorTest : TestBaseWithProfile() {

    private lateinit var processor: NsIncomingDataProcessor

    // Mock all dependencies
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var profileSource: ProfileSource
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setUp() {
        // Default preferences to allow data acceptance
        whenever(preferences.get(BooleanKey.NsClientAcceptCgmData)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientAcceptInsulin)).thenReturn(true)
        // Default NSClient to be enabled
        whenever(nsClientSource.isEnabled()).thenReturn(true)


        processor = NsIncomingDataProcessor(
            aapsLogger = aapsLogger,
            nsClientSource = nsClientSource,
            preferences = preferences,
            rxBus = rxBus,
            dateUtil = dateUtil,
            activePlugin = activePlugin,
            storeDataForDb = storeDataForDb,
            config = config,
            profileStoreProvider = profileStoreProvider,
            profileSource = profileSource,
            uiInteraction = uiInteraction
        )
    }

    @Test
    fun `processSgvs with V1 valid data stores glucose values`() {
        val sgvTime = now - T.mins(3).msecs()
        val sgvJson = JSONArray().put(JSONObject().apply {
            put("_id", "test_id")
            put("device", "G6-Reader")
            put("mills", sgvTime)
            put("mgdl", 120)
            put("direction", "Flat")
            put("filtered", 122000.0)
        })

        val result = processor.processSgvs(sgvJson, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb).addToGlucoseValues(argThat {
            size == 1 && this[0].value == 120.0 && this[0].timestamp == sgvTime
        })
        verify(preferences).put(BooleanNonKey.ObjectivesBgIsAvailableInNs, true)
    }

    @Test
    fun `processSgvs with V3 valid data stores glucose values`() {
        val sgvTime = now - T.mins(2).msecs()
        val sgvList = listOf(
            NSSgvV3(
                device = "share2",
                date = sgvTime,
                identifier = "abcdef",
                utcOffset = null,
                isValid = true,
                units = NsUnits.MG_DL,
                sgv = 130.0,
                direction = Direction.FORTY_FIVE_UP,
                noise = 1.0,
                filtered = 132.0,
                unfiltered = 131.0
            )
        )

        val result = processor.processSgvs(sgvList, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb).addToGlucoseValues(argThat {
            size == 1 &&
                get(0).value == 130.0 &&
                get(0).timestamp == sgvTime &&
                get(0).trendArrow == TrendArrow.FORTY_FIVE_UP
        })
    }

    @Test
    fun `processSgvs returns false when NSClient is disabled and not full sync`() {
        whenever(nsClientSource.isEnabled()).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientAcceptCgmData)).thenReturn(false)
        val sgvList = listOf<NSSgvV3>()

        val result = processor.processSgvs(sgvList, doFullSync = false)
        assertFalse(result)
        verify(storeDataForDb, never()).addToGlucoseValues(any())
    }

    @Test
    fun `processSgvs ignores SGV with future timestamp`() {
        val futureTime = now + T.mins(5).msecs()
        val sgvList = listOf(
            NSSgvV3(
                device = "share2",
                date = futureTime,
                identifier = "abcdef",
                utcOffset = null,
                isValid = true,
                units = NsUnits.MG_DL,
                sgv = 140.0,
                direction = Direction.FORTY_FIVE_UP,
                noise = 1.0,
                filtered = 132.0,
                unfiltered = 131.0
            )
        )

        val result = processor.processSgvs(sgvList, doFullSync = false)
        assertFalse(result)
        verify(storeDataForDb, never()).addToGlucoseValues(any())
    }

    @Test
    fun `processTreatments with valid carb entry stores it`() {
        val carbTime = now - T.mins(10).msecs()
        val treatments = listOf<NSTreatment>(
            NSCarbs(
                identifier = "carb_id",
                date = carbTime,
                carbs = 20.0,
                duration = 0,
                utcOffset = null,
                isValid = true,
                eventType = EventType.CARBS_CORRECTION,
                notes = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null
            )
        )

        val result = processor.processTreatments(treatments, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb).addToCarbs(argThat {
            amount == 20.0 && timestamp == carbTime
        })
    }

    @Test
    fun `processTreatments with valid bolus entry stores it`() {
        val bolusTime = now - T.mins(20).msecs()
        val treatments = listOf<NSTreatment>(
            NSBolus(
                identifier = "bolus_id",
                date = bolusTime,
                insulin = 2.5,
                utcOffset = null,
                isValid = true,
                eventType = EventType.CORRECTION_BOLUS,
                notes = "Manual bolus",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                type = NSBolus.BolusType.NORMAL,
                isBasalInsulin = false
            )
        )

        val result = processor.processTreatments(treatments, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb).addToBoluses(argThat {
            amount == 2.5 && timestamp == bolusTime
        })
    }

    @Test
    fun `processTreatments ignores carbs if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(false)
        val treatments = listOf<NSTreatment>(
            NSCarbs(
                identifier = "carb_id",
                date = now,
                carbs = 20.0,
                duration = 0,
                utcOffset = null,
                isValid = true,
                eventType = EventType.CARBS_CORRECTION,
                notes = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToCarbs(any())
    }

    @Test
    fun `processProfile with newer remote profile loads it from store`() {
        val localProfileTime = now - T.days(1).msecs()
        val profileJson = JSONObject() // Dummy JSON
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileStore)).thenReturn(true)
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(localProfileTime)

        processor.processProfile(profileJson, doFullSync = false)
        verify(profileSource).loadFromStore(any())
    }

    @Test
    fun `processProfile does not load if preference is disabled`() {
        val localProfileTime = now - T.days(1).msecs()
        val profileJson = JSONObject()
        // Disable accepting profile from NS
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileStore)).thenReturn(false)
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(localProfileTime)

        processor.processProfile(profileJson, doFullSync = false)
        verify(profileSource, never()).loadFromStore(any())
    }

    @Test
    fun `processTreatments with valid temp target stores it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTempTarget)).thenReturn(true)
        val ttTime = now - T.mins(30).msecs()
        val treatments = listOf<NSTreatment>(
            NSTemporaryTarget(
                identifier = "tt_id",
                date = ttTime,
                isValid = true,
                eventType = EventType.TEMPORARY_TARGET,
                notes = "Exercise TT",
                targetTop = 140.0,
                targetBottom = 140.0,
                duration = 3600 * 1000, // minutes
                utcOffset = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                units = NsUnits.MG_DL,
                reason = NSTemporaryTarget.Reason.ACTIVITY
            )
        )

        val result = processor.processTreatments(treatments, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb).addToTemporaryTargets(argThat {
            highTarget == 140.0 &&
                lowTarget == 140.0 &&
                timestamp == ttTime &&
                duration == 3600 * 1000L
        })
    }

    @Test
    fun `processTreatments with invalid temp target is ignored`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTempTarget)).thenReturn(true)
        val ttTime = now - T.mins(30).msecs()
        val treatments = listOf<NSTreatment>(
            NSTemporaryTarget(
                identifier = "tt_id",
                date = ttTime,
                isValid = true,
                eventType = EventType.TEMPORARY_TARGET,
                notes = "Exercise TT",
                targetTop = 0.0,
                targetBottom = 140.0,
                duration = 3600 * 1000, // minutes
                utcOffset = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                units = NsUnits.MG_DL,
                reason = NSTemporaryTarget.Reason.ACTIVITY
            )
        )

        val result = processor.processTreatments(treatments, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb, never()).addToTemporaryTargets(any())
    }

    @Test
    fun `processTreatments ignores temp target if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTempTarget)).thenReturn(false)
        val ttTime = now - T.mins(30).msecs()
        val treatments = listOf<NSTreatment>(
            NSTemporaryTarget(
                identifier = "tt_id",
                date = ttTime,
                isValid = true,
                eventType = EventType.TEMPORARY_TARGET,
                targetTop = 150.0,
                targetBottom = 150.0,
                duration = 30,
                utcOffset = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                notes = null,
                units = NsUnits.MG_DL,
                reason = NSTemporaryTarget.Reason.ACTIVITY
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToTemporaryTargets(any())
    }

    @Test
    fun `processTreatments with valid temp basal stores it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(true)
        val basalTime = now - T.mins(45).msecs()
        val treatments = listOf<NSTreatment>(
            NSTemporaryBasal(
                identifier = "basal_id",
                date = basalTime,
                isValid = true,
                eventType = EventType.TEMPORARY_BASAL,
                notes = "Activity basal",
                duration = 30 * 60000,
                absolute = 0.5,
                percent = 0.0,
                rate = 0.5,
                utcOffset = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                isAbsolute = true,
                type = NSTemporaryBasal.Type.NORMAL
            )
        )

        val result = processor.processTreatments(treatments, doFullSync = false)
        assertTrue(result)
        verify(storeDataForDb).addToTemporaryBasals(argThat {
            rate == 0.5 && timestamp == basalTime && duration == 30 * 60000L
        })
    }

    @Test
    fun `processTreatments with zero temp basal stores it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(true)
        val basalTime = now - T.mins(40).msecs()
        val treatments = listOf<NSTreatment>(
            NSTemporaryBasal(
                identifier = "basal_id_zero",
                date = basalTime,
                isValid = true,
                eventType = EventType.TEMPORARY_BASAL,
                notes = "Suspend",
                duration = 30 * 60000,
                absolute = 0.0,
                percent = -100.0, // Zero basal
                rate = 0.0,
                utcOffset = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                isAbsolute = false,
                type = NSTemporaryBasal.Type.NORMAL
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb).addToTemporaryBasals(argThat {
            !isAbsolute && rate == 0.0 && timestamp == basalTime && duration == 30 * 60000L
        })
    }

    @Test
    fun `processTreatments ignores temp basal if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(false)
        val basalTime = now - T.mins(45).msecs()
        val treatments = listOf<NSTreatment>(
            NSTemporaryBasal(
                identifier = "basal_id",
                date = basalTime,
                isValid = true,
                eventType = EventType.TEMPORARY_BASAL,
                duration = 30,
                absolute = 0.5,
                percent = 0.0,
                rate = 0.0,
                utcOffset = null,
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                notes = null,
                isAbsolute = true,
                type = NSTemporaryBasal.Type.NORMAL
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToTemporaryBasals(any())
    }

    @Test
    fun `processTreatments with valid effective profile switch stores it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        val switchTime = now - T.hours(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSEffectiveProfileSwitch(
                identifier = "profile_switch_id",
                date = switchTime,
                isValid = true,
                eventType = EventType.PROFILE_SWITCH,
                originalProfileName = "Workout",
                originalCustomizedName = "Workout",
                originalDuration = 0, // A duration of 0 means a permanent switch
                originalTimeshift = 0,
                originalEnd = 0,
                originalPercentage = 100,
                utcOffset = null,
                notes = "Switched to Workout profile",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                profileJson = validProfile.toPureNsJson(dateUtil)
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb).addToEffectiveProfileSwitches(argThat {
            originalProfileName == "Workout" && timestamp == switchTime
        })
    }

    @Test
    fun `processTreatments ignores effective profile switch if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(false)
        val switchTime = now - T.hours(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSEffectiveProfileSwitch(
                identifier = "profile_switch_id",
                date = switchTime,
                isValid = true,
                eventType = EventType.PROFILE_SWITCH,
                originalProfileName = "Workout",
                originalCustomizedName = "Workout",
                originalDuration = 0, // A duration of 0 means a permanent switch
                originalTimeshift = 0,
                originalEnd = 0,
                originalPercentage = 100,
                utcOffset = null,
                notes = "Switched to Workout profile",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                profileJson = validProfile.toPureNsJson(dateUtil)
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToEffectiveProfileSwitches(any())
    }

    @Test
    fun `processTreatments ignores invalid effective profile switch`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        val switchTime = now - T.hours(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSEffectiveProfileSwitch(
                identifier = "profile_switch_id",
                date = switchTime,
                isValid = true,
                eventType = EventType.PROFILE_SWITCH,
                originalProfileName = "Workout",
                originalCustomizedName = "Workout",
                originalDuration = 0, // A duration of 0 means a permanent switch
                originalTimeshift = 0,
                originalEnd = 0,
                originalPercentage = 100,
                utcOffset = null,
                notes = "Switched to Workout profile",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                profileJson = JSONObject()
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToEffectiveProfileSwitches(any())
    }

    @Test
    fun `processTreatments with valid profile switch stores it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        val switchTime = now - T.hours(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSProfileSwitch(
                identifier = "profile_switch_id",
                date = switchTime,
                isValid = true,
                eventType = EventType.PROFILE_SWITCH,
                profile = "Workout",
                originalProfileName = "Workout",
                originalDuration = 0, // A duration of 0 means a permanent switch
                timeShift = 0,
                percentage = 100,
                duration = 0,
                utcOffset = null,
                notes = "Switched to Workout profile",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                profileJson = validProfile.toPureNsJson(dateUtil)
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb).addToProfileSwitches(argThat {
            profileName == "Workout" && timestamp == switchTime
        })
    }

    @Test
    fun `processTreatments ignores profile switch if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(false)
        val switchTime = now - T.hours(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSProfileSwitch(
                identifier = "profile_switch_id",
                date = switchTime,
                isValid = true,
                eventType = EventType.PROFILE_SWITCH,
                profile = "Workout",
                originalProfileName = "Workout",
                originalDuration = 0, // A duration of 0 means a permanent switch
                timeShift = 0,
                percentage = 100,
                duration = 0,
                utcOffset = null,
                notes = "Switched to Workout profile",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                profileJson = validProfile.toPureNsJson(dateUtil)
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToProfileSwitches(any())
    }

    @Test
    fun `processTreatments ignores invalid profile switch`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        val switchTime = now - T.hours(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSProfileSwitch(
                identifier = "profile_switch_id",
                date = switchTime,
                isValid = true,
                eventType = EventType.PROFILE_SWITCH,
                profile = "Workout",
                originalProfileName = "Workout",
                originalDuration = 0, // A duration of 0 means a permanent switch
                timeShift = 0,
                percentage = 100,
                duration = 0,
                utcOffset = null,
                notes = "Switched to Workout profile",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                profileJson = JSONObject()
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToProfileSwitches(any())
    }

    @Test
    fun `processTreatments with valid offline event stores it when preferences are enabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptRunningMode)).thenReturn(true)
        val eventTime = now - T.days(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSOfflineEvent(
                identifier = "offline_id",
                date = eventTime,
                isValid = true,
                eventType = EventType.APS_OFFLINE,
                utcOffset = null,
                notes = "Device was offline",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                duration = 60000L,
                originalDuration = 60000,
                reason = NSOfflineEvent.Reason.OTHER,
                mode = NSOfflineEvent.Mode.UNKNOWN,
                autoForced = false,
                reasons = null
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb).addToRunningModes(argThat {
            timestamp == eventTime
        })
    }

    @Test
    fun `processTreatments ignores offline event if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptRunningMode)).thenReturn(false)
        val eventTime = now - T.days(1).msecs()
        val treatments = listOf<NSTreatment>(
            NSOfflineEvent(
                identifier = "offline_id",
                date = eventTime,
                isValid = true,
                eventType = EventType.APS_OFFLINE,
                utcOffset = null,
                notes = "Device was offline",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                duration = 60000L,
                originalDuration = 60000,
                reason = NSOfflineEvent.Reason.OTHER,
                mode = NSOfflineEvent.Mode.UNKNOWN,
                autoForced = false,
                reasons = null
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToRunningModes(any())
    }

    @Test
    fun `processTreatments with valid extended bolus stores it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(true)
        val extendedBolusTime = now - T.hours(2).msecs()
        val treatments = listOf<NSTreatment>(
            NSExtendedBolus(
                identifier = "extended_bolus_id",
                date = extendedBolusTime,
                isValid = true,
                eventType = EventType.COMBO_BOLUS,
                enteredinsulin = 3.0,
                duration = 120 * 60000L,
                utcOffset = null,
                notes = "Pizza bolus",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                isEmulatingTempBasal = false,
                rate = 1.5
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb).addToExtendedBoluses(argThat {
            amount == 3.0 && timestamp == extendedBolusTime && this.duration == 120 * 60000L
        })
    }

    @Test
    fun `processTreatments ignores extended bolus if preference is disabled`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptTbrEb)).thenReturn(false)
        val extendedBolusTime = now - T.hours(2).msecs()
        val treatments = listOf<NSTreatment>(
            NSExtendedBolus(
                identifier = "extended_bolus_id",
                date = extendedBolusTime,
                isValid = true,
                eventType = EventType.COMBO_BOLUS,
                enteredinsulin = 3.0,
                duration = 120 * 60000L,
                utcOffset = null,
                notes = "Pizza bolus",
                pumpId = null,
                endId = null,
                pumpType = null,
                pumpSerial = null,
                isEmulatingTempBasal = false,
                rate = 1.5
            )
        )

        processor.processTreatments(treatments, doFullSync = false)
        verify(storeDataForDb, never()).addToExtendedBoluses(any())
    }

    @Test
    fun `processFood with V3 list data stores it`() {
        val foodTime = now - T.mins(30).msecs()
        val foodList = listOf(
            NSFood(
                identifier = "food_id_1",
                date = foodTime,
                isValid = true,
                carbs = 30,
                name = "sandwich",
                portion = 1.0,
                unit = "piece"
            )
        )

        processor.processFood(foodList)
        verify(storeDataForDb).addToFoods(argThat {
            this.size == 1 && this[0].carbs == 30 && this[0].name == "sandwich"
        })
    }

    @Test
    fun `processFood with V1 JSONArray data stores it`() {
        val foodTime = now - T.mins(45).msecs()
        val foodJsonArray = JSONArray().put(JSONObject().apply {
            put("_id", "food_json_id")
            put("type", "food")
            put("created_at", dateUtil.toISOString(foodTime))
            put("carbs", 50)
            put("name", "pizza")
            put("portion", 1.0)
            put("carbs", 10)
        })

        processor.processFood(foodJsonArray)
        verify(storeDataForDb).addToFoods(argThat {
            size == 1 && this[0].carbs == 10 && this[0].name == "pizza"
        })
    }

    @Test
    fun `processFood with V1 remove action creates invalidation entry`() {
        val foodJsonArray = JSONArray().put(JSONObject().apply {
            put("_id", "food_to_delete_id")
            put("type", "food")
            put("action", "remove") // Remove action
        })

        processor.processFood(foodJsonArray)

        verify(storeDataForDb).addToFoods(argThat {
            this.size == 1 && !this[0].isValid && this[0].ids.nightscoutId == "food_to_delete_id"
        })
    }

    @Test
    fun `processFood ignores non-food types in V1 JSONArray`() {
        val foodJsonArray = JSONArray().put(JSONObject().apply {
            put("_id", "some_other_id")
            put("type", "not_food") // Invalid type
            put("created_at", dateUtil.toISOString(now))
            put("carbs", 10)
        })

        processor.processFood(foodJsonArray)
        verify(storeDataForDb, times(1)).addToFoods(argThat { isEmpty() })
    }

    @Test
    fun `processFood handles empty list and array without error`() {
        val emptyList = emptyList<NSFood>()
        val emptyArray = JSONArray()

        processor.processFood(emptyList)
        processor.processFood(emptyArray)

        verify(storeDataForDb, times(2)).addToFoods(argThat { isEmpty() })
    }
}
