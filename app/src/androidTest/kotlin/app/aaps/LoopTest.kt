package app.aaps

import android.annotation.SuppressLint
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

class LoopTest @Inject constructor() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var localProfileManager: LocalProfileManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var l: L
    @Inject lateinit var config: Config
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var iobCobCalculator: IobCobCalculator

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    private val profileData = "{\"_id\":\"653f90bc89f99714b4635b33\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.449Z\"," +
        "\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":14.618357917185001},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":8.99591256442154},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":10.12040163497423},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":11.244890705526924},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":13.493868846632308},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":13.493868846632308},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":13.493868846632308}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55361111111111}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.306},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.306},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.334},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.337},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.35},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.388},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.388},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.391},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.365},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.34},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.337},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.35},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.36},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.351},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.349},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.359},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.354},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.336},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.339},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.357},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.368},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.327},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.318},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.318}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"GMT\"}},\"app\":\"AAPS\",\"utcOffset\":120,\"identifier\":\"6b503f6c-b676-5746-b331-658b03d50843\",\"srvModified\":1698763282534,\"srvCreated\":1698664636986,\"subject\":\"Phone\"},{\"_id\":\"6511a54e3c60c21734f1988b\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.4490000Z\",\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":14.618357917185001},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":8.99591256442154},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":10.12040163497423},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":11.244890705526924},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":13.493868846632308},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":13.493868846632308},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":13.493868846632308}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55361111111111}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.306},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.306},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.334},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.337},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.35},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.388},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.388},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.391},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.365},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.34},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.337},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.35},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.36},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.351},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.349},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.359},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.354},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.336},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.339},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.357},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.368},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.327},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.318},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.318}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"Europe/Prague\"}}}"

    @Before
    fun inject() {
        context.androidInjector().inject(this)
        // Cancel background workers from previous tests, clear all caches and DB
        androidx.work.WorkManager.getInstance(context).cancelAllWork()
        iobCobCalculator.clearCache()
        runBlocking { persistenceLayer.clearDatabases() }
        (profileFunction as ProfileFunctionImpl).cache.clear()
    }

    @After
    fun tearDown() {
        rxHelper.clear()
        loop.lastRun = null
        objectivesPlugin.objectives.forEach { it.startedOn = 0 }
        (profileFunction as ProfileFunctionImpl).cache.clear()
        persistenceLayer.clearDatabases()
    }

    @Test
    fun loopTest() = runBlocking {
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
        )
        rxHelper.listen(EventLoopSetLastRunGui::class.java)
        rxHelper.listen(EventResetOpenAPSGui::class.java)
        rxHelper.listen(EventOpenAPSUpdateGui::class.java)
        rxHelper.listen(EventAPSCalculationFinished::class.java)
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
        assertThat(localProfileManager.profile).isNotNull()

        // Create a profile switch
        assertThat(profileFunction.getProfile()).isNull()
        val result = profileFunction.createProfileSwitch(
            profileStore = localProfileManager.profile ?: error("No profile"),
            profileName = localProfileManager.profile?.getDefaultProfileName() ?: error("No profile"),
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = Sources.ProfileSwitchDialog,
            note = "Test profile switch",
            listValues = listOf(
                ValueWithUnit.SimpleString(localProfileManager.profile?.getDefaultProfileName() ?: ""),
                ValueWithUnit.Percent(100)
            ),
            iCfg = ICfg("Test", insulinEndTime = 5 * 3600 * 1000L, insulinPeakTime = 75 * 60 * 1000L)
        )
        assertThat(result).isNotNull()

        // wait until PS is processed and EPS is created in DB
        assertThat(rxHelper.waitUntil("step3: profile available") { runBlocking { profileFunction.getProfile() } != null }).isTrue()
        assertThat(profileFunction.getProfile()).isNotNull()

        // Wait until pump has received the profile (baseBasalRate > 0)
        assertThat(rxHelper.waitUntil("step3: pump profile set") { runBlocking { pumpSync.expectedPumpState() }.profile != null }).isTrue()

        // Loop should run — may get "NO APS SELECTED" (no glucose) or a real result (stale glucose cache)
        rxHelper.listen(EventLoopUpdateGui::class.java)
        loop.invoke("test3", allowNotification = false)
        // Accept either: error event (no glucose) or update event (APS produced result from cached data)
        assertThat(
            rxHelper.waitUntil("step4: loop completed") {
                rxHelper.waitFor(EventLoopSetLastRunGui::class.java, maxSeconds = 1, comment = "step4").first ||
                    rxHelper.waitFor(EventLoopUpdateGui::class.java, maxSeconds = 1, comment = "step4").first
            }
        ).isTrue()

        // Let generate some BGs
        val now = dateUtil.now()
        val glucoseValues = mutableListOf<GV>()
        glucoseValues += GV(timestamp = now - 5 * 60000, value = 100.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 4 * 60000, value = 110.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 3 * 60000, value = 120.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 2 * 60000, value = 130.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 1 * 60000, value = 140.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        glucoseValues += GV(timestamp = now - 0 * 60000, value = 150.0, raw = 0.0, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        assertThat(persistenceLayer.insertCgmSourceData(Sources.Random, glucoseValues, emptyList(), null).inserted.size).isEqualTo(6)

        // GV insertion triggers calculation via observeChanges(GV) → scheduleHistoryDataChange (5s debounce)
        // IobCobOref1Worker may exit early ("No bucketed data") so EventAutosensCalculationFinished
        // is not guaranteed. Wait for EventAPSCalculationFinished which fires when loop runs.
        assertThat(rxHelper.waitFor(EventAPSCalculationFinished::class.java, maxSeconds = 60, comment = "step6").first).isTrue()
        Thread.sleep(5000)
        assertThat(loop.lastRun).isNotNull()
    }
}