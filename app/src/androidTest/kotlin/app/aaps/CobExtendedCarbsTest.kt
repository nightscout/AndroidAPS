package app.aaps

import android.annotation.SuppressLint
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.ICfg
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
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
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
    @Inject lateinit var localProfileManager: LocalProfileManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var l: L
    @Inject lateinit var config: Config
    @Inject lateinit var loop: Loop
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin

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
        loop.lastRun = null
        objectivesPlugin.objectives.forEach { it.startedOn = 0 }
        (profileFunction as ProfileFunctionImpl).cache.clear()
        persistenceLayer.clearDatabases()
    }

    // ==================== Helpers ====================

    private fun setupEnvironment() = runBlocking {
        rxHelper.listen(EventAutosensCalculationFinished::class.java)
        l.findByName(LTag.EVENTS.name).enabled = true
        assertThat(config.APS).isTrue()

        setupProfileAndObjectives()
    }

    private suspend fun setupProfileAndObjectives() {
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
        )

        objectivesPlugin.onStart()
        objectivesPlugin.objectives[0].startedOn = 1

        (profileFunction as ProfileFunctionImpl).cache.clear()
        nsIncomingDataProcessor.processProfile(JSONObject(profileData), false)
        assertThat(localProfileManager.profile).isNotNull()

        // Start collecting EPS changes before creating profile switch
        val epsDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(EPS::class.java).first()
            }
        }

        val store = localProfileManager.profile ?: error("No profile")
        val profileName = store.getDefaultProfileName() ?: error("No profile")
        val iCfg = store.getSpecificProfile(profileName)?.iCfg ?: ICfg("Insulin", peak = 75, dia = 5.0, concentration = 1.0)
        val result = profileFunction.createProfileSwitch(
            profileStore = store,
            profileName = profileName,
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = Sources.ProfileSwitchDialog,
            note = "Test",
            listValues = listOf(
                ValueWithUnit.SimpleString(profileName),
                ValueWithUnit.Percent(100)
            ),
            iCfg = iCfg
        )
        assertThat(result).isNotNull()

        // Wait for EPS flow emission (replaces old EventEffectiveProfileSwitchChanged)
        val epsList = epsDeferred.await()
        aapsLogger.info(LTag.CORE, "EPS flow emitted ${epsList.size} entries")
        assertThat(epsList).isNotEmpty()

        // Also wait until profile is available
        assertThat(rxHelper.waitUntil("profile available") { runBlocking { profileFunction.getProfile() } != null }).isTrue()
    }

    private suspend fun insertBgData(now: Long, durationMinutes: Int, bgValueProvider: (minutesAgo: Int) -> Double, trendArrow: TrendArrow = TrendArrow.FLAT) {
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
        val insertResult = persistenceLayer.insertCgmSourceData(Sources.Random, glucoseValues, emptyList(), null)
        aapsLogger.info(LTag.CORE, "Inserted ${insertResult.inserted.size} BG readings")
    }

    private suspend fun insertFlatBgData(now: Long, durationMinutes: Int, bgValue: Double) {
        insertBgData(now, durationMinutes, { bgValue })
    }

    private suspend fun insertCarbs(timestamp: Long, amount: Double, durationMs: Long) {
        @SuppressLint("CheckResult")
        persistenceLayer.insertOrUpdateCarbs(
            CA(timestamp = timestamp, amount = amount, duration = durationMs),
            action = Action.CARBS,
            source = Sources.CarbDialog,
            note = null
        )
    }

    /**
     * Insert BG data and wait for:
     * 1. GV flow emission (replaces old EventNewBG)
     * 2. Autosens calculation to complete (replaces old EventNewHistoryData + EventAutosensCalculationFinished)
     */
    private suspend fun insertBgAndWait(now: Long) {
        // Start collecting GV changes before inserting
        val gvDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(GV::class.java).first()
            }
        }

        rxHelper.resetState(EventAutosensCalculationFinished::class.java)
        insertFlatBgData(now, 60, 100.0)

        // Wait for GV flow emission (replaces old EventNewBG)
        val gvList = gvDeferred.await()
        aapsLogger.info(LTag.CORE, "GV flow emitted ${gvList.size} entries")
        assertThat(gvList).isNotEmpty()

        // Wait for autosens calculation triggered by BG insertion
        assertThat(rxHelper.waitFor(EventAutosensCalculationFinished::class.java, maxSeconds = 60, comment = "initial calc").first).isTrue()
        delay(2000)
    }

    /**
     * Trigger recalculation by inserting a new BG and wait for autosens to complete.
     */
    private suspend fun triggerCalculationAndWait(now: Long) {
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
        persistenceLayer.insertCgmSourceData(Sources.Random, newBg, emptyList(), null)
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
    private suspend fun assertCobBounded(totalCarbs: Double) {
        val timeline = collectCobTimeline()
        logCobTimeline(timeline)

        for ((_, cob) in timeline) {
            assertThat(cob).isAtMost(totalCarbs)
        }

        val cobInfo = iobCobCalculator.getCobInfo("test")
        assertThat(cobInfo.displayCob ?: 0.0).isAtMost(totalCarbs)

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
    private suspend fun assertCobReachedZero() {
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
    fun extendedCarbs35gOver2h_cobBounded() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 30 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
    }

    @Test
    fun extendedCarbs50gOver3h_cobBounded() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 45 * 60_000L, 50.0, 3 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(50.0)
    }

    @Test
    fun instantCarbs20g_cobBounded() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 15 * 60_000L, 20.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(20.0)
    }

    @Test
    fun mixedInstantAndExtended_cobBounded() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 20 * 60_000L, 15.0, 0)
        insertCarbs(now - 10 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(50.0)
    }

    @Test
    fun twoExtendedEntries_cobBounded() = runBlocking {
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
    fun extendedCarbs_cobDecreases() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 50 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
        assertCobDecreasingTail()
    }

    @Test
    fun instantCarbs_cobDecreases() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 30 * 60_000L, 20.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(20.0)
        assertCobDecreasingTail()
    }

    @Test
    fun mixedCarbs_cobDecreases() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        insertBgAndWait(now)
        insertCarbs(now - 40 * 60_000L, 15.0, 0)
        insertCarbs(now - 30 * 60_000L, 20.0, 1 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
        assertCobDecreasingTail()
    }

    // ==================== COB reaches zero after absorption ====================

    @Test
    fun extendedCarbs_cobReachesZero() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        // Start collecting GV changes
        val gvDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(GV::class.java).first()
            }
        }
        rxHelper.resetState(EventAutosensCalculationFinished::class.java)
        insertFlatBgData(now, 240, 100.0)
        gvDeferred.await()
        insertCarbs(now - 4 * 60 * 60_000L, 10.0, 15 * 60_000L)
        assertThat(rxHelper.waitFor(EventAutosensCalculationFinished::class.java, maxSeconds = 60, comment = "autosens").first).isTrue()
        Thread.sleep(2000)

        assertCobBounded(10.0)
        assertCobReachedZero()
    }

    @Test
    fun instantCarbs_cobReachesZero() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        val gvDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(GV::class.java).first()
            }
        }
        rxHelper.resetState(EventAutosensCalculationFinished::class.java)
        insertFlatBgData(now, 240, 100.0)
        gvDeferred.await()
        insertCarbs(now - 4 * 60 * 60_000L, 10.0, 0)
        assertThat(rxHelper.waitFor(EventAutosensCalculationFinished::class.java, maxSeconds = 60, comment = "autosens").first).isTrue()
        Thread.sleep(2000)

        assertCobBounded(10.0)
        assertCobReachedZero()
    }

    // ==================== Rising BG accelerates absorption ====================

    @Test
    fun risingBg_extendedCarbs_cobBoundedAndDecreases() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        val gvDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(GV::class.java).first()
            }
        }
        rxHelper.resetState(EventAutosensCalculationFinished::class.java)
        insertBgData(now, 60, { minutesAgo -> 200.0 - minutesAgo * (100.0 / 60.0) }, TrendArrow.FORTY_FIVE_UP)
        gvDeferred.await()
        insertCarbs(now - 30 * 60_000L, 35.0, 2 * 60 * 60_000L)
        triggerCalculationAndWait(now)

        assertCobBounded(35.0)
        assertCobDecreasingTail()
    }

    @Test
    fun risingBg_instantCarbs_cobBoundedAndDecreases() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        val gvDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(GV::class.java).first()
            }
        }
        rxHelper.resetState(EventAutosensCalculationFinished::class.java)
        insertBgData(now, 60, { minutesAgo -> 180.0 - minutesAgo * (100.0 / 60.0) }, TrendArrow.FORTY_FIVE_UP)
        gvDeferred.await()
        insertCarbs(now - 20 * 60_000L, 20.0, 0)
        triggerCalculationAndWait(now)

        assertCobBounded(20.0)
        assertCobDecreasingTail()
    }

    @Test
    fun risingBg_extendedCarbs_cobReachesZero() = runBlocking {
        setupEnvironment()
        val now = dateUtil.now()

        val gvDeferred = CoroutineScope(Dispatchers.IO).async {
            withTimeout(40_000) {
                persistenceLayer.observeChanges(GV::class.java).first()
            }
        }
        rxHelper.resetState(EventAutosensCalculationFinished::class.java)
        insertBgData(now, 240, { minutesAgo -> 250.0 - minutesAgo * (170.0 / 240.0) }, TrendArrow.FORTY_FIVE_UP)
        gvDeferred.await()
        insertCarbs(now - 4 * 60 * 60_000L, 10.0, 15 * 60_000L)
        assertThat(rxHelper.waitFor(EventAutosensCalculationFinished::class.java, maxSeconds = 60, comment = "autosens").first).isTrue()
        Thread.sleep(2000)

        assertCobBounded(10.0)
        assertCobReachedZero()
    }
}
