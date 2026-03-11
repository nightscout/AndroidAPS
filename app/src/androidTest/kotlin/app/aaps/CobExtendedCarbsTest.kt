package app.aaps

import android.annotation.SuppressLint
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Integration tests verifying COB calculation with real worker pipeline.
 *
 * Uses full Dagger DI with real:
 * - Room database (in-memory)
 * - AppRepository (expandCarbs + fromTo filter)
 * - PersistenceLayerImpl
 * - IobCobCalculator → IobCobOrefWorker (COB calculation)
 * - AutosensDataObject (deductAbsorbedCarbs, removeOldCarbs, cloneCarbsList)
 * - fromCarbs() extension
 *
 * The fix (issue #4596): IobCobOrefWorker queries carbs with exclusive start
 * (bgTime - 5min + 1ms) to prevent double-counting at window boundaries.
 */
class CobExtendedCarbsTest @Inject constructor() {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var l: L
    @Inject lateinit var config: Config
    @Inject lateinit var loop: Loop
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin

    @get:Rule
    var runtimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)!!

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    private val profileData = "{\"_id\":\"653f90bc89f99714b4635b33\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.449Z\"," +
        "\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":10}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.3}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}],\"units\":\"mmol\",\"timezone\":\"GMT\"}},\"app\":\"AAPS\",\"utcOffset\":0}"

    @Before
    fun inject() {
        context.androidInjector().inject(this)
    }

    @After
    fun tearDown() {
        rxHelper.clear()
        // Reset in-memory state to avoid affecting other tests
        loop.lastRun = null
        objectivesPlugin.objectives.forEach { it.startedOn = 0 }
        (profileFunction as ProfileFunctionImpl).cache.clear()
        persistenceLayer.clearDatabases()
    }

    // ==================== Helpers ====================

    private fun setupEnvironment() {
        rxHelper.listen(EventEffectiveProfileSwitchChanged::class.java)
        rxHelper.listen(EventNewBG::class.java)
        rxHelper.listen(EventNewHistoryData::class.java)
        rxHelper.listen(EventAutosensCalculationFinished::class.java)
        l.findByName(LTag.EVENTS.name).enabled = true
        assertThat(config.APS).isTrue()

        setupProfileAndObjectives()
        assertThat(rxHelper.waitFor(EventEffectiveProfileSwitchChanged::class.java, comment = "profile").first).isTrue()
        assertThat(profileFunction.getProfile()).isNotNull()
    }

    private fun setupProfileAndObjectives() {
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
            listValues = listOf(ValueWithUnit.SimpleString("Test"))
        ).blockingGet()

        objectivesPlugin.onStart()
        objectivesPlugin.objectives[0].startedOn = 1

        (profileFunction as ProfileFunctionImpl).cache.clear()
        nsIncomingDataProcessor.processProfile(JSONObject(profileData), false)
        assertThat(activePlugin.activeProfileSource.profile).isNotNull()

        val result = profileFunction.createProfileSwitch(
            profileStore = activePlugin.activeProfileSource.profile ?: error("No profile"),
            profileName = activePlugin.activeProfileSource.profile?.getDefaultProfileName() ?: error("No profile"),
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = Sources.ProfileSwitchDialog,
            note = "Test",
            listValues = listOf(
                ValueWithUnit.SimpleString(activePlugin.activeProfileSource.profile?.getDefaultProfileName() ?: ""),
                ValueWithUnit.Percent(100)
            )
        )
        assertThat(result).isTrue()
    }

    private fun insertBgData(now: Long, durationMinutes: Int, bgValueProvider: (minutesAgo: Int) -> Double, trendArrow: TrendArrow = TrendArrow.FLAT) {
        val glucoseValues = mutableListOf<GV>()
        for (i in durationMinutes downTo 0) {
            glucoseValues += GV(
                timestamp = now - i * 60_000L,
                value = bgValueProvider(i),
                raw = 0.0,
                noise = null,
                trendArrow = trendArrow,
                sourceSensor = SourceSensor.RANDOM
            )
        }
        val insertResult = persistenceLayer.insertCgmSourceData(Sources.Random, glucoseValues, emptyList(), null).blockingGet()
        aapsLogger.info(LTag.CORE, "Inserted ${insertResult.inserted.size} BG readings")
    }

    private fun insertFlatBgData(now: Long, durationMinutes: Int, bgValue: Double) {
        insertBgData(now, durationMinutes, { bgValue })
    }

    private fun insertCarbs(timestamp: Long, amount: Double, durationMs: Long) {
        @SuppressLint("CheckResult")
        persistenceLayer.insertOrUpdateCarbs(
            CA(timestamp = timestamp, amount = amount, duration = durationMs),
            action = Action.CARBS,
            source = Sources.CarbDialog,
            note = null
        ).blockingGet()
    }

    private fun insertBgAndWait(now: Long) {
        insertFlatBgData(now, 60, 100.0)
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "bg").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "history").first).isTrue()
    }

    private fun triggerCalculationAndWait(now: Long) {
        rxHelper.resetState(EventNewBG::class.java)
        rxHelper.resetState(EventAutosensCalculationFinished::class.java)

        val newBg = listOf(
            GV(
                timestamp = now + 60_000L,
                value = 100.0,
                raw = 0.0,
                noise = null,
                trendArrow = TrendArrow.FLAT,
                sourceSensor = SourceSensor.RANDOM
            )
        )
        persistenceLayer.insertCgmSourceData(Sources.Random, newBg, emptyList(), null).blockingGet()
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "trigger").first).isTrue()
        assertThat(rxHelper.waitFor(EventAutosensCalculationFinished::class.java, maxSeconds = 60, comment = "autosens").first).isTrue()
        Thread.sleep(2000)
    }

    /** Collect COB values from all autosens data buckets, ordered by time */
    private fun collectCobTimeline(): List<Pair<Long, Double>> {
        val ads = iobCobCalculator.ads
        val bucketedData = ads.getBucketedDataTableCopy() ?: return emptyList()
        val timeline = mutableListOf<Pair<Long, Double>>()
        for (bd in bucketedData) {
            val ad: AutosensData? = ads.getAutosensDataAtTime(bd.timestamp)
            if (ad != null) {
                timeline.add(Pair(bd.timestamp, ad.cob))
            }
        }
        return timeline.sortedBy { it.first }
    }

    private fun logCobTimeline(timeline: List<Pair<Long, Double>>) {
        for ((timestamp, cob) in timeline) {
            if (cob > 0) {
                aapsLogger.info(LTag.CORE, "COB at ${dateUtil.dateAndTimeString(timestamp)}: $cob")
            }
        }
    }

    /** Assert COB never exceeds totalCarbs across all data sources */
    private fun assertCobBounded(totalCarbs: Double) {
        val timeline = collectCobTimeline()
        logCobTimeline(timeline)

        // Every autosens entry must be bounded
        for ((_, cob) in timeline) {
            assertThat(cob).isAtMost(totalCarbs)
        }

        // getCobInfo must be bounded
        val cobInfo = iobCobCalculator.getCobInfo("test")
        assertThat(cobInfo.displayCob ?: 0.0).isAtMost(totalCarbs)

        // mealCOB must be bounded
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        assertThat(mealData.mealCOB).isAtMost(totalCarbs)

        val peakCob = timeline.maxOfOrNull { it.second } ?: 0.0
        aapsLogger.info(LTag.CORE, "Peak COB: $peakCob (limit: $totalCarbs), current: ${cobInfo.displayCob}, mealCOB: ${mealData.mealCOB}")
    }

    /** Assert COB is monotonically non-increasing in the tail (last N entries with COB > 0) */
    private fun assertCobDecreasingTail(tailSize: Int = 3) {
        val timeline = collectCobTimeline()
        val activeCob = timeline.filter { it.second > 0 }

        if (activeCob.size >= tailSize) {
            val tail = activeCob.takeLast(tailSize)
            for (i in 1 until tail.size) {
                assertThat(tail[i].second).isAtMost(tail[i - 1].second)
            }
            aapsLogger.info(LTag.CORE, "COB tail (last $tailSize): ${tail.map { String.format("%.1f", it.second) }}")
        }
    }

    /** Assert current COB is zero (carbs fully absorbed) */
    private fun assertCobReachedZero() {
        val cobInfo = iobCobCalculator.getCobInfo("test")
        val currentCob = cobInfo.displayCob ?: 0.0
        aapsLogger.info(LTag.CORE, "Current COB (expecting zero): $currentCob")
        assertThat(currentCob).isEqualTo(0.0)

        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        aapsLogger.info(LTag.CORE, "mealCOB (expecting zero): ${mealData.mealCOB}")
        assertThat(mealData.mealCOB).isEqualTo(0.0)
    }

    // ==================== COB never exceeds total carbs (issue #4596) ====================

    @Test
    fun extendedCarbs35gOver2h_cobBounded() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 30 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
    }

    @Test
    fun extendedCarbs50gOver3h_cobBounded() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 45 * 60_000L, 50.0, 3 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(50.0)
    }

    @Test
    fun instantCarbs20g_cobBounded() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 15 * 60_000L, 20.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(20.0)
    }

    @Test
    fun mixedInstantAndExtended_cobBounded() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 20 * 60_000L, 15.0, 0)
        insertCarbs(now - 10 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(50.0)
    }

    @Test
    fun twoExtendedEntries_cobBounded() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 40 * 60_000L, 20.0, 1 * 60 * 60_000L)
        insertCarbs(now - 20 * 60_000L, 30.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(50.0)
    }

    // ==================== COB decreases over time ====================

    @Test
    fun extendedCarbs_cobDecreases() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        // 35g/2h inserted 50min ago — some chunks absorbed, COB should be declining
        insertCarbs(now - 50 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
        assertCobDecreasingTail()
    }

    @Test
    fun instantCarbs_cobDecreases() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        // 20g instant inserted 30min ago — absorption in progress
        insertCarbs(now - 30 * 60_000L, 20.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(20.0)
        assertCobDecreasingTail()
    }

    @Test
    fun mixedCarbs_cobDecreases() {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        // 15g instant 40min ago + 20g/1h extended 30min ago
        insertCarbs(now - 40 * 60_000L, 15.0, 0)
        insertCarbs(now - 30 * 60_000L, 20.0, 1 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
        assertCobDecreasingTail()
    }

    // ==================== COB reaches zero after absorption ====================

    @Test
    fun extendedCarbs_cobReachesZero() {
        setupEnvironment()
        val now = dateUtil.now()

        // Need enough BG history to cover full absorption window
        // Insert 4 hours of BG data so the worker processes enough buckets
        insertFlatBgData(now, 240, 100.0)
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "bg").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "history").first).isTrue()

        // 10g/15min extended carbs inserted 4 hours ago — well past absorption time
        insertCarbs(now - 4 * 60 * 60_000L, 10.0, 15 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(10.0)
        assertCobReachedZero()
    }

    @Test
    fun instantCarbs_cobReachesZero() {
        setupEnvironment()
        val now = dateUtil.now()

        // Insert 4 hours of BG data
        insertFlatBgData(now, 240, 100.0)
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "bg").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "history").first).isTrue()

        // 10g instant carbs inserted 4 hours ago — fully absorbed
        insertCarbs(now - 4 * 60 * 60_000L, 10.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(10.0)
        assertCobReachedZero()
    }

    // ==================== Rising BG accelerates absorption ====================

    @Test
    fun risingBg_extendedCarbs_cobBoundedAndDecreases() {
        setupEnvironment()
        val now = dateUtil.now()

        // Rising BG: 100 → 200 mg/dL over 60 minutes (~1.67 mg/dL per minute)
        insertBgData(now, 60, { minutesAgo -> 200.0 - minutesAgo * (100.0 / 60.0) }, TrendArrow.FORTY_FIVE_UP)
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "bg").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "history").first).isTrue()

        // 35g/2h extended carbs inserted 30min ago
        insertCarbs(now - 30 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        // COB must still be bounded
        assertCobBounded(35.0)
        // Rising BG → positive deviation → faster absorption → COB should be decreasing
        assertCobDecreasingTail()
    }

    @Test
    fun risingBg_instantCarbs_cobBoundedAndDecreases() {
        setupEnvironment()
        val now = dateUtil.now()

        // Rising BG: 80 → 180 mg/dL over 60 minutes
        insertBgData(now, 60, { minutesAgo -> 180.0 - minutesAgo * (100.0 / 60.0) }, TrendArrow.FORTY_FIVE_UP)
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "bg").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "history").first).isTrue()

        // 20g instant carbs inserted 20min ago
        insertCarbs(now - 20 * 60_000L, 20.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(20.0)
        assertCobDecreasingTail()
    }

    @Test
    fun risingBg_extendedCarbs_cobReachesZeroFasterThanFlat() {
        setupEnvironment()
        val now = dateUtil.now()

        // Rising BG over 4 hours: 80 → 250 mg/dL
        insertBgData(now, 240, { minutesAgo -> 250.0 - minutesAgo * (170.0 / 240.0) }, TrendArrow.FORTY_FIVE_UP)
        assertThat(rxHelper.waitFor(EventNewBG::class.java, comment = "bg").first).isTrue()
        assertThat(rxHelper.waitFor(EventNewHistoryData::class.java, comment = "history").first).isTrue()

        // 10g/15min extended carbs inserted 4 hours ago
        insertCarbs(now - 4 * 60 * 60_000L, 10.0, 15 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(10.0)
        // With rising BG, absorption is faster — COB should reach zero
        assertCobReachedZero()
    }
}
