package app.aaps

import android.annotation.SuppressLint
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventDeviceStatusChange
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventFoodDatabaseChanged
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventRunningModeChange
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.rx.events.EventTreatmentChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.database.AppRepository
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Inject
import javax.inject.Provider

class CompatDbHelperTest @Inject constructor() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var l: L
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var bolusWizardProvider: Provider<BolusWizard>
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    private val profileData = "{\"_id\":\"653f90bc89f99714b4635b33\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.449Z\"," +
        "\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":14.618357917185001},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":8.99591256442154},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":10.12040163497423},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":11.244890705526924},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":13.493868846632308},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":13.493868846632308},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":13.493868846632308}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55361111111111}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.306},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.306},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.334},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.337},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.35},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.388},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.388},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.391},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.365},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.34},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.337},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.35},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.36},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.351},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.349},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.359},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.354},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.336},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.339},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.357},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.368},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.327},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.318},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.318}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"GMT\"}},\"app\":\"AAPS\",\"utcOffset\":120,\"identifier\":\"6b503f6c-b676-5746-b331-658b03d50843\",\"srvModified\":1698763282534,\"srvCreated\":1698664636986,\"subject\":\"Phone\"},{\"_id\":\"6511a54e3c60c21734f1988b\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.4490000Z\",\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":14.618357917185001},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":8.99591256442154},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":10.12040163497423},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":11.244890705526924},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":13.493868846632308},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":13.493868846632308},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":13.493868846632308}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55361111111111}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.306},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.306},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.334},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.337},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.35},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.388},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.388},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.391},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.365},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.34},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.337},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.35},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.36},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.351},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.349},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.359},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.354},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.336},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.339},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.357},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.368},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.327},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.318},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.318}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"Europe/Prague\"}}}"

    @Before
    fun inject() {
        context.androidInjector().inject(this)
    }

    @After
    fun tearDown() {
        rxHelper.clear()
        repository.clearDatabases()
    }

    @SuppressLint("CheckResult")
    @Test
    fun compatDbHelperTest() {

        // Prepare
        rxHelper.listen(EventNewBG::class.java)
        rxHelper.listen(EventNewHistoryData::class.java)
        rxHelper.listen(EventTreatmentChange::class.java)
        rxHelper.listen(EventTempBasalChange::class.java)
        rxHelper.listen(EventExtendedBolusChange::class.java)
        rxHelper.listen(EventProfileSwitchChanged::class.java)
        rxHelper.listen(EventEffectiveProfileSwitchChanged::class.java)
        rxHelper.listen(EventTempTargetChange::class.java)
        rxHelper.listen(EventTherapyEventChange::class.java)
        rxHelper.listen(EventFoodDatabaseChanged::class.java)
        rxHelper.listen(EventRunningModeChange::class.java)
        rxHelper.listen(EventDeviceStatusChange::class.java)

        // Enable event logging
        l.findByName(LTag.EVENTS.name).enabled = true

        // EventProfileSwitchChanged tested in LoopTest
        // EventEffectiveProfileSwitchChanged tested in LoopTest
        // EventNewBG and EventNewHistoryData tested in LoopTest

        val now = dateUtil.now()

        // Set Profile in ProfilePlugin
        nsIncomingDataProcessor.processProfile(JSONObject(profileData), false)
        assertThat(activePlugin.activeProfileSource.profile).isNotNull()

        // Create a profile switch
        assertThat(profileFunction.getProfile()).isNull()
        val psResult = profileFunction.createProfileSwitch(
            profileStore = activePlugin.activeProfileSource.profile ?: error("No profile"),
            profileName = activePlugin.activeProfileSource.profile?.getDefaultProfileName() ?: error("No profile"),
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = Sources.ProfileSwitchDialog,
            note = "Test profile switch",
            listValues = listOf(
                ValueWithUnit.SimpleString(activePlugin.activeProfileSource.profile?.getDefaultProfileName() ?: ""),
                ValueWithUnit.Percent(100)
            )
        )
        assertThat(psResult).isTrue()
        // wait until PS is processed by pump and EventEffectiveProfileSwitchChanged is received
        assertThat(rxHelper.waitFor(EventEffectiveProfileSwitchChanged::class.java, comment = "step3").first).isTrue()
        assertThat(profileFunction.getProfile()).isNotNull()

        // Let generate some carbs
        rxHelper.resetState(EventTreatmentChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        var detailedBolusInfo = DetailedBolusInfo().also {
            it.eventType = TE.Type.CARBS_CORRECTION
            it.carbs = 10.0
            it.context = context
            it.notes = "Note"
            it.carbsDuration = T.hours(1).msecs()
            it.carbsTimestamp = now
        }
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        // EventTreatmentChange should be triggered
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step1").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step2").first).isTrue()

        // Let generate some bolus
        rxHelper.resetState(EventTreatmentChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        detailedBolusInfo = DetailedBolusInfo().also {
            it.eventType = TE.Type.CORRECTION_BOLUS
            it.insulin = 1.0
            it.context = null
            it.notes = "Note"
        }
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        // EventTreatmentChange should be triggered
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step3").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step4").first).isTrue()

        //BCR
        rxHelper.resetState(EventTreatmentChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        val bcr = bolusWizardProvider.get().doCalc(
            profile = profileFunction.getProfile() ?: error("No profile"),
            profileName = profileFunction.getProfileName(),
            tempTarget = null,
            carbs = 10,
            cob = 1.1,
            bg = 238.0,
            correction = 0.2,
            useBg = true,
            useCob = true,
            includeBolusIOB = true,
            includeBasalIOB = true,
            useSuperBolus = false,
            useTT = false,
            useTrend = false,
            useAlarm = false
        ).createBolusCalculatorResult()
        persistenceLayer.insertOrUpdateBolusCalculatorResult(bcr).blockingGet()
        // EventTreatmentChange should be triggered
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step5").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step6").first).isTrue()

        // TB
        rxHelper.resetState(EventTempBasalChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        val tb = TB(
            timestamp = dateUtil.now(),
            type = TB.Type.NORMAL,
            isAbsolute = true,
            rate = 0.7,
            duration = T.mins(30).msecs(),
            ids = IDs(pumpId = 123450, pumpType = PumpType.CELLNOVO, pumpSerial = "23424242342")
        )
        persistenceLayer.syncPumpTemporaryBasal(tb, TB.Type.NORMAL).blockingGet()
        // EventTempBasalChange should be triggered
        assertThat(rxHelper.waitFor(EventTempBasalChange::class.java, comment = "step7").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step8").first).isTrue()

        // EB
        rxHelper.resetState(EventExtendedBolusChange::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        val eb = EB(
            timestamp = dateUtil.now(),
            isEmulatingTempBasal = false,
            amount = 0.7,
            duration = T.mins(30).msecs(),
            ids = IDs(pumpId = 123451, pumpType = PumpType.CELLNOVO, pumpSerial = "23424242342")
        )
        persistenceLayer.syncPumpExtendedBolus(eb).blockingGet()
        // EventExtendedBolusChange should be triggered
        assertThat(rxHelper.waitFor(EventExtendedBolusChange::class.java, comment = "step9").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step10").first).isTrue()

        // TT
        rxHelper.resetState(EventTempTargetChange::class.java)
        val tt = TT(
            timestamp = dateUtil.now(),
            reason = TT.Reason.ACTIVITY,
            highTarget = 120.0,
            lowTarget = 100.0,
            duration = T.mins(30).msecs(),
            ids = IDs(pumpId = 123452, pumpType = PumpType.CELLNOVO, pumpSerial = "23424242342")
        )
        persistenceLayer.insertAndCancelCurrentTemporaryTarget(tt, Action.TT, Sources.Aaps, null, listOf()).blockingGet()
        // EventTempTargetChange should be triggered
        assertThat(rxHelper.waitFor(EventTempTargetChange::class.java, comment = "step11").first).isTrue()

        // TE
        rxHelper.resetState(EventTherapyEventChange::class.java)
        val te = TE(
            timestamp = dateUtil.now(),
            type = TE.Type.ANNOUNCEMENT,
            glucoseUnit = GlucoseUnit.MMOL,
            duration = T.mins(30).msecs(),
            ids = IDs(pumpId = 123453, pumpType = PumpType.CELLNOVO, pumpSerial = "23424242342")
        )
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(te, dateUtil.now(), Action.CAREPORTAL, Sources.Aaps, null, listOf()).blockingGet()
        // EventTherapyEventChange should be triggered
        assertThat(rxHelper.waitFor(EventTherapyEventChange::class.java, comment = "step12").first).isTrue()

        // Food
        rxHelper.resetState(EventFoodDatabaseChanged::class.java)
        val fd = FD(
            name = "name",
            carbs = 24,
            portion = 1.0
        )
        persistenceLayer.syncNsFood(listOf(fd)).blockingGet()
        // EventFoodDatabaseChanged should be triggered
        assertThat(rxHelper.waitFor(EventFoodDatabaseChanged::class.java, comment = "step13").first).isTrue()

        // RM
        rxHelper.resetState(EventRunningModeChange::class.java)
        val rm = RM(
            timestamp = dateUtil.now(),
            mode = RM.Mode.DISCONNECTED_PUMP,
            duration = T.hours(1).msecs()
        )
        persistenceLayer.insertOrUpdateRunningMode(rm, Action.DISCONNECT, Sources.Aaps, null, listOf()).blockingGet()
        // EventOfflineChange should be triggered
        assertThat(rxHelper.waitFor(EventRunningModeChange::class.java, comment = "step13").first).isTrue()

        // DS
        rxHelper.resetState(EventDeviceStatusChange::class.java)
        val ds = DS(
            timestamp = dateUtil.now(),
            uploaderBattery = 90,
            isCharging = true
        )
        persistenceLayer.insertDeviceStatus(ds)
        // EventDeviceStatusChange should be triggered
        assertThat(rxHelper.waitFor(EventDeviceStatusChange::class.java, comment = "step13").first).isTrue()
    }
}