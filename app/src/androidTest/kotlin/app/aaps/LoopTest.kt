package app.aaps

import android.annotation.SuppressLint
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.AppRepository
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

class LoopTest @Inject constructor() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var l: L
    @Inject lateinit var config: Config
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer

    @get:Rule
    var runtimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)!!

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
    fun loopTest() {
        // Prepare
        persistenceLayer.clearDatabases()
        @SuppressLint("CheckResult")
        persistenceLayer.insertOrUpdateRunningMode(
            runningMode = RM(
                timestamp = dateUtil.now(),
                mode = RM.Mode.CLOSED_LOOP,
                autoForced = false,
                duration = 0
            ),
            action = Action.CLOSED_LOOP_MODE,
            source = Sources.Aaps,
            listValues = listOf(ValueWithUnit.SimpleString("Migration"))
        ).blockingGet()
        rxHelper.listen(EventEffectiveProfileSwitchChanged::class.java)
        rxHelper.listen(EventLoopSetLastRunGui::class.java)
        rxHelper.listen(EventResetOpenAPSGui::class.java)
        rxHelper.listen(EventOpenAPSUpdateGui::class.java)
        rxHelper.listen(EventNewBG::class.java)
        rxHelper.listen(EventNewHistoryData::class.java)
        rxHelper.listen(EventAutosensCalculationFinished::class.java)
        rxHelper.listen(EventAPSCalculationFinished::class.java)
        rxHelper.listen(EventLoopUpdateGui::class.java)
        objectivesPlugin.onStart()


        // Enable event logging
        l.findByName(LTag.EVENTS.name).enabled = true

        // Are we running full flavor?
        assertThat(config.APS).isTrue()

        // Loop should be limited by unfinished objectives
        loop.invoke("test1", allowNotification = false)
        var loopStatusEvent = rxHelper.waitFor(EventLoopSetLastRunGui::class.java, comment = "step1")
        assertThat(loopStatusEvent.first).isTrue()
        assertThat((loopStatusEvent.second as EventLoopSetLastRunGui).text).contains("Objective 1 not started")

        // So start objectives
        objectivesPlugin.objectives[0].startedOn = 1

        // Now there should be missing profile
        (profileFunction as ProfileFunctionImpl).cache.clear()
        loop.invoke("test2", allowNotification = false)
        loopStatusEvent = rxHelper.waitFor(EventLoopSetLastRunGui::class.java, comment = "step2")
        assertThat(loopStatusEvent.first).isTrue()
        assertThat((loopStatusEvent.second as EventLoopSetLastRunGui).text).contains("NO PROFILE SET")

        // Set Profile in ProfilePlugin
        nsIncomingDataProcessor.processProfile(JSONObject(profileData), false)
        assertThat(activePlugin.activeProfileSource.profile).isNotNull()

        // Create a profile switch
        assertThat(profileFunction.getProfile()).isNull()
        val result = profileFunction.createProfileSwitch(
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
        assertThat(result).isTrue()

        // wait until PS is processed by pump and EventEffectiveProfileSwitchChanged is received
        assertThat(rxHelper.waitFor(EventEffectiveProfileSwitchChanged::class.java, comment = "step3").first).isTrue()
        assertThat(profileFunction.getProfile()).isNotNull()

        // Loop should fail on no result from APS plugin
        loop.invoke("test3", allowNotification = false)
        loopStatusEvent = rxHelper.waitFor(EventLoopSetLastRunGui::class.java, comment = "step4")
        assertThat(loopStatusEvent.first).isTrue()
        assertThat((loopStatusEvent.second as EventLoopSetLastRunGui).text).contains("NO APS SELECTED OR PROVIDED RESULT")
        val apsStatusEvent = rxHelper.waitFor(EventResetOpenAPSGui::class.java, comment = "step5")
        assertThat(apsStatusEvent.first).isTrue()
        assertThat((apsStatusEvent.second as EventResetOpenAPSGui).text).contains("No glucose data available")
        assertThat(loop.lastRun).isNull()

        // Let generate some BGs
        rxHelper.resetState(EventNewBG::class.java)
        rxHelper.resetState(EventAPSCalculationFinished::class.java)
        rxHelper.resetState(EventLoopUpdateGui::class.java)
        val resetSequence = rxHelper.currentSequence()
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
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "step6").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "step7").first).isTrue()
        // it should trigger loop, so wait for result
        assertThat(rxHelper.waitForAfter(EventAPSCalculationFinished::class.java, resetSequence, comment = "step8").first).isTrue()
        val apsSequence = rxHelper.lastSequence(EventAPSCalculationFinished::class.java)
            ?: error("EventAPSCalculationFinished not recorded")
        assertThat(apsSequence).isGreaterThan(resetSequence)
        assertThat(rxHelper.waitForAfter(EventLoopUpdateGui::class.java, apsSequence, comment = "step9").first).isTrue()
        val loopUpdateSequence = rxHelper.lastSequence(EventLoopUpdateGui::class.java)
            ?: error("EventLoopUpdateGui not recorded")
        assertThat(loopUpdateSequence).isGreaterThan(apsSequence)
        assertThat(loop.lastRun).isNotNull()
    }
}
