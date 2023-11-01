package app.aaps.database.persistence

import androidx.test.core.app.ApplicationProvider
import app.aaps.TestApplication
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
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
import app.aaps.core.interfaces.rx.events.EventOfflineChange
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.rx.events.EventTreatmentChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.helpers.RxHelper
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

class CompatDbHelperTest @Inject constructor() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var l: L
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var commandQueue: CommandQueue

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
    }
    @Test
    fun dbHelperTest() {
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
        rxHelper.listen(EventOfflineChange::class.java)
        rxHelper.listen(EventDeviceStatusChange::class.java)

        // Enable event logging
        l.findByName(LTag.EVENTS.name).enabled = true

        // Set Profile in ProfilePlugin
        rxHelper.resetState(EventProfileSwitchChanged::class.java)
        rxHelper.resetState(EventEffectiveProfileSwitchChanged::class.java)
        nsIncomingDataProcessor.processProfile(JSONObject(profileData))
        assertThat(activePlugin.activeProfileSource.profile).isNotNull()
        // Create a profile switch
        assertThat(profileFunction.getProfile()).isNull()
        val ps = profileFunction.createProfileSwitch(
            profileStore = activePlugin.activeProfileSource.profile ?: error("No profile"),
            profileName = activePlugin.activeProfileSource.profile?.getDefaultProfileName() ?: error("No profile"),
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.PROFILE_SWITCH,
            source = Sources.ProfileSwitchDialog,
            note = "Test profile switch",
            listValues = listOf(
                ValueWithUnit.SimpleString(activePlugin.activeProfileSource.profile?.getDefaultProfileName() ?: ""),
                ValueWithUnit.Percent(100)
            )
        )
        assertThat(ps).isTrue()
        // EventProfileSwitchChanged should be fired
        assertThat(rxHelper.waitFor(EventProfileSwitchChanged::class.java, comment = "step1").first).isTrue()
        // After pump processing EventEffectiveProfileSwitchChanged should be fired
        assertThat(rxHelper.waitFor(EventEffectiveProfileSwitchChanged::class.java, comment = "step2").first).isTrue()

        // Let generate some BGs
        rxHelper.resetState(EventNewBG::class.java)
        rxHelper.resetState(EventNewHistoryData::class.java)
        val now = dateUtil.now()
        val glucoseValues = mutableListOf<GV>()
        glucoseValues += GV(timestamp = now - 5 * 60000, value = 100.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 4 * 60000, value = 110.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 3 * 60000, value = 120.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 2 * 60000, value = 130.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 1 * 60000, value = 140.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 0 * 60000, value = 150.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        assertThat(persistenceLayer.insertCgmSourceData(Sources.Random, glucoseValues, emptyList(), null).blockingGet().inserted.size).isEqualTo(6)

        // EventNewBG should be triggered
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "step3").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step4").first).isTrue()

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
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step5").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step6").first).isTrue()

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
        assertThat(rxHelper.waitFor(EventTreatmentChange::class.java, comment = "step7").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step8").first).isTrue()

    }
}