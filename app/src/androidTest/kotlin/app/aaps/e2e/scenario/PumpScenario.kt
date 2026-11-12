package app.aaps.e2e.scenario

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.WorkManager
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.extensions.singleBlock
import app.aaps.core.objects.extensions.singleTargetBlock
import app.aaps.di.BaseTestApp
import app.aaps.di.EmulatedOptions
import app.aaps.di.testGraphs
import app.aaps.e2e.clearAppSettings
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one pump through what a user and the loop do, through the real command queue, and after each step compares
 * what AAPS stored with what the emulated pump did. No screen is used: the scenarios test the core of AAPS and the
 * driver, and the screens have their own tests.
 *
 * The checks are the same for every pump, because they are AAPS rules, not driver details:
 * - one bolus record for each bolus, with the amount the pump gave ([checkBolusRecords])
 * - a bolus the user stopped raises no alarm ([checkNoAlarm])
 * - the temp basal AAPS has is the one the pump runs ([checkTempBasal])
 * - the link closes when the queue is empty ([checkLinkClosed])
 * - a status read does not repeat itself ([checkNoBurst])
 *
 * The emulator runs fast unless the run is started with `-e emulatorSpeed real`: shorter actions, never a faster clock.
 */
class PumpScenario(private val pump: PumpUnderTest) {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<BaseTestApp>()

    val commandQueue: CommandQueue get() = testGraphs.commandQueue
    val control: PumpEmulatorControl get() = pump.control()

    val speed: EmulatorSpeed =
        if (InstrumentationRegistry.getArguments().getString(SPEED_ARGUMENT) == SPEED_REAL) EmulatorSpeed.REAL else EmulatorSpeed.FAST

    /** Records from before this are not part of the scenario. */
    var startedAt = 0L
        private set

    /** For a bolus that runs while the test does something else, as stopping it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Start and restart ------------------------------------------------------------------

    /**
     * A configured app with this pump as the active one and a profile running, as a user has it before pairing.
     * @param restartable the database is a file, so run 2 of an app restart finds it, see [saveForKill]
     */
    fun bringUp(restartable: Boolean = false) {
        clearAppSettings(instrumentation.targetContext)
        if (restartable) app.useDatabaseFile(fresh = true)
        EmulatedOptions.enabled = setOf(pump.emulatorOption)
        grantPermissions()
        startedAt = System.currentTimeMillis()
        seedConfiguredApp()
        startApp()
        seedProfile()
        activateProfile()
        seedRunningMode()
    }

    /**
     * The driver stopped and started again between commands, with the queue held, as a settings import does it. A
     * driver must then hold the same pump as before: its start loads everything again, and its stop drops everything.
     * Not the same as an app restart: in-memory state of other objects stays, and the app start order is not run. See
     * [saveForKill] for that.
     */
    fun reloadDriver() {
        val plugin = testGraphs.activePlugin.activePumpInternal as PluginBase
        val held = runBlocking {
            commandQueue.withHold("scenario: driver reload", RELOAD_HOLD) {
                plugin.setPluginEnabledAwaiting(PluginType.PUMP, false)
                plugin.setPluginEnabledAwaiting(PluginType.PUMP, true)
            }
        }
        check(held) { "The queue could not be held for the reload" }
        pump.awaitStarted(this)
    }

    /**
     * Run 1 of an app restart: what the emulated pump holds is saved to a file, and the test ends without any cleanup,
     * as a killed app. The instrumentation run ends after it, and the process with it.
     */
    fun saveForKill() {
        val saved = checkNotNull(control.saveState()) { "${pump.pluginName}: its emulator cannot save what it holds" }
        killFile().writeBytes(saved)
        killed = true
    }

    /**
     * Run 2 of an app restart, in a new process: the app starts on the database and settings of run 1, with the
     * emulated pump as it was saved. The time between the runs passed for the pump too, as for a real one.
     */
    fun resumeAfterKill() {
        val saved = killFile().readBytes()
        app.useDatabaseFile(fresh = false)
        EmulatedOptions.enabled = setOf(pump.emulatorOption)
        grantPermissions()
        app.preloadEmulators { pump.restoreEmulator(saved, it) }
        // The database is the one of run 1, so every record in it belongs to this scenario.
        startedAt = 0
        startApp()
    }

    private fun killFile() = File(instrumentation.targetContext.filesDir, KILL_FILE)

    /** Set by [saveForKill]: a killed app does not clean up. */
    private var killed = false

    /** As `MainApp.onCreate`: the plugins published, the active pump chosen from the settings and started. */
    private fun startApp() {
        testGraphs.pluginStore.plugins = testGraphs.allPlugins(testGraphs.aapsLogger)
        testGraphs.configBuilder.initialize()
        testGraphs.config.initCompleted()
        // Not activePump: that is the concentration wrapper around the driver.
        awaitTrue("${pump.pluginName} is the active pump") { testGraphs.activePlugin.activePumpInternal.javaClass.simpleName == pump.pluginName }
        pump.control().speed = speed
        pump.awaitStarted(this)
        appStarted = true
    }

    /** A skipped test never started the app, so there is no driver to stop. */
    private var appStarted = false

    fun tearDown() {
        if (killed) return
        scope.cancel()
        runCatching { commandQueue.clear() }
        // The clear leaves a running command running. Stopped under it, the driver connects again for it and writes its
        // pump into the settings of the next test, which then starts as paired: its driver reads status in the middle of
        // the pairing, and the pairing hung (seen with Equil).
        runCatching { awaitQueueIdle(QUEUE_END_TIMEOUT) }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { testGraphs.activePlugin.activePump.disconnect("test end") }
        if (appStarted) stopDriver()
        EmulatedOptions.enabled = emptySet()
        clearAppSettings(instrumentation.targetContext)
    }

    /**
     * The next test has a new graph, but an Android service of the driver is made once for the process. Left bound, the
     * next test's driver binds to this one, and its link then runs on the objects of this test's graph. So the driver is
     * stopped as a real one is, which unbinds its service, and the test waits until the system has destroyed it.
     */
    private fun stopDriver() {
        val plugin = testGraphs.activePlugin.activePumpInternal as PluginBase
        runCatching { runBlocking { plugin.setPluginEnabledAwaiting(PluginType.PUMP, false) } }
        val driverPackage = plugin.javaClass.packageName
        awaitTrue("the services of ${pump.pluginName} are stopped", SERVICE_STOP_TIMEOUT) { runningServices().none { it.startsWith(driverPackage) } }
    }

    /** The services of this app that run now. The call only returns the caller's own services, which is all this needs. */
    @Suppress("DEPRECATION")
    private fun runningServices(): List<String> =
        (instrumentation.targetContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .filter { it.service.packageName == instrumentation.targetContext.packageName }
            .map { it.service.className }

    // ---- Steps ------------------------------------------------------------------------------

    /** Pairs the pump, and then sends it the profile, as the keep alive worker does when a pump lacks it. */
    fun pair() {
        pump.pair(this)
        testGraphs.rxBus.send(EventProfileChangeRequested())
        // A profile write can take longer than the default wait: a Diaconn G8 waits 30 s after it, as a Carelevo does.
        // AAPS can run the profile before the pump has it: the write is queued a moment later. A fault a test sets after
        // the pairing then hit that write in place of the test's own.
        awaitTrue("AAPS runs the profile and the pump gives its basal of $BASAL_RATE U/h", PROFILE_TIMEOUT) {
            aapsRunsBasal(BASAL_RATE) && pumpGivesBasal(BASAL_RATE)
        }
        awaitQueueIdle()
        // No wait here: a pump writes its records by its own clock, which can be a second or two behind the phone, so a
        // record of the first command can have a time just before the first profile. AAPS must keep it.
    }

    fun unpair() = pump.unpair(this)

    /**
     * A profile switch to [percentage] % of the profile, as the user makes one, and the new basal sent to the pump as the
     * keep alive worker does when the pump lacks it. [checkStatusShown] then finds whether the pump holds it.
     */
    fun switchProfile(percentage: Int) {
        storeProfileSwitch(percentage)
        testGraphs.rxBus.send(EventProfileChangeRequested())
        // A driver may take a new profile only some time after the one before (Carelevo: 30 s), or the answer of the
        // pump may be lost: the keep alive worker then asks again, as here, until AAPS and the pump give the new basal.
        val basal = BASAL_RATE * percentage / PERCENT
        awaitTrue("AAPS runs the profile at $percentage % and the pump gives its basal of $basal U/h", PROFILE_TIMEOUT) {
            if (aapsRunsBasal(basal) && pumpGivesBasal(basal)) true
            else {
                awaitQueueIdle()
                testGraphs.rxBus.send(EventProfileChangeRequested())
                SystemClock.sleep(PROFILE_RETRY_MS)
                false
            }
        }
        awaitQueueIdle()
    }

    /**
     * A profile switch to [percentage] % that AAPS sends to the pump once: when it stores the switch, with no second try.
     *
     * @return true when AAPS took the write as done and runs the switch, false when it reported a failed write. AAPS runs
     * the switch with the basal the pump reports, so after a write taken as done by mistake it runs the old basal.
     */
    fun profileWrittenOnce(percentage: Int): Boolean {
        storeProfileSwitch(percentage)
        var done = false
        awaitTrue("the write of the profile at $percentage % ends", PROFILE_TIMEOUT) {
            val running = runBlocking { testGraphs.persistenceLayer.getEffectiveProfileSwitchActiveAt(testGraphs.dateUtil.now()) }
            done = running?.originalPercentage == percentage
            done || NotificationId.FAILED_UPDATE_PROFILE in shownNotifications()
        }
        awaitQueueIdle()
        return done
    }

    /** A profile switch to [percentage] % of the profile, as the user makes one. Storing it makes AAPS send it to the pump. */
    private fun storeProfileSwitch(percentage: Int) {
        val store = checkNotNull(testGraphs.profileRepository.profile.value) { "No profile store" }
        val switch = runBlocking {
            testGraphs.profileFunction.createProfileSwitch(
                profileStore = store,
                profileName = PROFILE_NAME,
                durationInMinutes = 0,
                percentage = percentage,
                timeShiftInHours = 0,
                timestamp = testGraphs.dateUtil.now(),
                action = Action.PROFILE_SWITCH,
                source = Sources.Aaps,
                listValues = emptyList(),
                iCfg = testGraphs.insulinManager.insulins.first()
            )
        }
        checkNotNull(switch) { "The profile switch to $percentage % was refused" }
    }

    /**
     * Once the pump has a profile, AAPS runs it: the running profile has the basal at that percentage. Its `percentage`
     * is not the sign, the switch AAPS runs then has the values with the percentage already in them.
     */
    private fun aapsRunsBasal(basal: Double): Boolean =
        runBlocking { testGraphs.profileFunction.getProfile() }?.let { abs(it.getBasalTimeFromMidnight(0) - basal) < RATE_TOLERANCE } == true

    /** Every hour: a pump that takes a profile in parts can hold a part of it. A pump that does not tell counts as giving it. */
    private fun pumpGivesBasal(basal: Double): Boolean =
        control.state().basal?.all { abs(it - basal) <= BASAL_TOLERANCE } ?: true

    /**
     * The queue drops a status read while another one waits or runs, and reports it as failed. A driver start sends one,
     * and during a bolus it waits for the bolus end. So wait for that one first: the step must read the pump itself.
     */
    fun readStatus(): PumpEnactResult {
        awaitTrue("the status read before this one is done", BOLUS_TIMEOUT) { !commandQueue.statusInQueue() }
        return queued { commandQueue.readStatus("scenario") }
    }

    /** A bolus to its end, straight through the queue. This path raises no alarm, see [userBolus]. */
    fun bolus(units: Double): PumpEnactResult = queued { commandQueue.bolus(DetailedBolusInfo().also { it.insulin = units }) }

    /**
     * A bolus as the user gives it from the insulin dialog, to its end: through `MasterActionExecutor`, the one place
     * that raises the BOLUS_DELIVERY_FAILED alarm when the pump does not give a bolus. True when it went through, also
     * when the user stopped it.
     */
    fun userBolus(units: Double): Boolean = await(startUserBolus(units))

    /** A bolus the user gives and then stops, once the pump has given at least [stopAfter] units. True as [userBolus]. */
    fun stoppedBolus(units: Double, stopAfter: Double): Boolean {
        val answer = startUserBolus(units)
        awaitGiven(stopAfter)
        stopBolus()
        return await(answer)
    }

    /** The user stops the running bolus, as the stop button does. */
    fun stopBolus() = commandQueue.cancelAllBoluses(null)

    /**
     * The answer of a command that runs while the test goes on, as from [startBolus]. Then the queue is idle: a status
     * read the driver asked for can wait for the end of a bolus, so the same limit holds for it.
     */
    fun <T> await(answer: Deferred<T>, timeoutMs: Long = BOLUS_TIMEOUT): T =
        runBlocking { withTimeout(timeoutMs) { answer.await() } }.also { awaitQueueIdle(timeoutMs) }

    /** A temp basal that runs while the test goes on. */
    fun startTempBasal(rate: Double, minutes: Int): Deferred<PumpEnactResult> {
        val profile = checkNotNull(runBlocking { testGraphs.profileFunction.getProfile() }) { "No profile runs" }
        val answer = CompletableDeferred<PumpEnactResult>()
        scope.launch { answer.complete(commandQueue.tempBasalAbsolute(rate, minutes, true, profile, PumpSync.TemporaryBasalType.NORMAL)) }
        return answer
    }

    /** A bolus that runs while the test goes on. The answer of the queue comes at its end. */
    fun startBolus(units: Double): Deferred<PumpEnactResult> {
        followBolus()
        val answer = CompletableDeferred<PumpEnactResult>()
        scope.launch { answer.complete(commandQueue.bolus(DetailedBolusInfo().also { it.insulin = units })) }
        return answer
    }

    /** A bolus of [userBolus] that runs while the test goes on. The answer comes at its end. */
    fun startUserBolus(units: Double): Deferred<Boolean> {
        followBolus()
        val answer = CompletableDeferred<Boolean>()
        scope.launch {
            testGraphs.rootGraph.masterActionExecutor.deliverInsulin(
                insulin = units, note = null, source = Sources.InsulinDialog,
                onError = { answer.complete(false) }, onSuccess = { answer.complete(true) }
            )
        }
        return answer
    }

    private fun followBolus() {
        bolusReservoirBefore = checkNotNull(control.state().reservoir) { "A bolus the test follows needs a pump that reports its reservoir" }
    }

    /** Waits until the bolus of [startBolus] has given at least [units]. */
    fun awaitGiven(units: Double) {
        val before = bolusReservoirBefore
        awaitTrue("the pump gave $units U", BOLUS_TIMEOUT) {
            val state = control.state()
            state.bolusRunning && before - (state.reservoir ?: before) >= units
        }
    }

    private var bolusReservoirBefore = 0.0

    fun tempBasal(rate: Double, minutes: Int): PumpEnactResult {
        val profile = checkNotNull(runBlocking { testGraphs.profileFunction.getProfile() }) { "No profile runs" }
        return queued { commandQueue.tempBasalAbsolute(rate, minutes, true, profile, PumpSync.TemporaryBasalType.NORMAL) }
    }

    /**
     * A temp basal in percent, the way the temp basal dialog sets one on such a pump. A Dana takes an hour or more on
     * another command than the short one the loop uses.
     */
    fun tempBasalPercent(percent: Int, minutes: Int): PumpEnactResult {
        val profile = checkNotNull(runBlocking { testGraphs.profileFunction.getProfile() }) { "No profile runs" }
        return queued { commandQueue.tempBasalPercent(percent, minutes, true, profile, PumpSync.TemporaryBasalType.NORMAL) }
    }

    fun cancelTempBasal(): PumpEnactResult = queued { commandQueue.cancelTempBasal(enforceNew = true) }

    /**
     * A temp basal as the loop asks for one (`LoopPlugin.applyTBRRequest`): with `enforceNew = false`, so the driver may
     * keep a temp basal that already runs. [tempBasal] is the one of the dialog, which must always set it.
     */
    fun loopTempBasal(rate: Double, minutes: Int): PumpEnactResult {
        val profile = checkNotNull(runBlocking { testGraphs.profileFunction.getProfile() }) { "No profile runs" }
        return queued { commandQueue.tempBasalAbsolute(rate, minutes, false, profile, PumpSync.TemporaryBasalType.NORMAL) }
    }

    /** The end of a temp basal as the loop asks for it, back at the base rate: `enforceNew = false`. */
    fun loopCancelTempBasal(): PumpEnactResult = queued { commandQueue.cancelTempBasal(enforceNew = false) }

    /**
     * A status read on a new connection, as AAPS makes one after the link was closed for a while. Some drivers learn what
     * happened on the pump only then: Equil reads the pump's advertisement (battery, newest log entry) at a connect, and
     * Carelevo the patch's alarm snapshot. A status read can queue more reads of its own (Equil reads its log after it),
     * so it waits for those too.
     */
    fun readStatusOnNewLink() {
        checkLinkClosed()
        readStatus()
        awaitQueueIdle()
    }

    /**
     * The queue gives up on a link after [SHORT_CONNECTION_LIMIT_S] instead of about two minutes: the same path, a
     * shorter wait. Only this graph's queue; the next test starts with the real limit.
     */
    fun shortenQueueConnectionLimit() {
        testGraphs.rootGraph.commandExecutor.maxConnectionSeconds = SHORT_CONNECTION_LIMIT_S
    }

    private val pumpDescription get() = testGraphs.activePlugin.activePumpInternal.pumpDescription

    /** The pump can give an extended bolus, see [extendedBolus]. */
    val canGiveExtendedBolus: Boolean get() = pumpDescription.isExtendedBolusCapable

    /** The pump keeps daily totals that AAPS reads, see [readTdds]. */
    val canReadTdds: Boolean get() = pumpDescription.supportsTDDs

    /** The pump takes a temp basal in percent, see [tempBasalPercent]. */
    val canSetPercentTempBasal: Boolean get() = pumpDescription.tempBasalStyle and PumpDescription.PERCENT != 0

    fun extendedBolus(units: Double, minutes: Int): PumpEnactResult = queued { commandQueue.extendedBolus(units, minutes) }

    fun cancelExtendedBolus(): PumpEnactResult = queued { commandQueue.cancelExtended() }

    fun readTdds(): PumpEnactResult = queued { commandQueue.loadTDDs() }

    private fun queued(step: suspend () -> PumpEnactResult): PumpEnactResult = runBlocking { step() }.also { awaitQueueIdle() }

    // ---- Checks -----------------------------------------------------------------------------

    /**
     * AAPS stored each bolus the pump gave, once, with the amount it gave, and no bolus the pump did not give.
     * @param timeoutMs records are written by the driver as it learns of them. Some learn only once a bolus would have ended.
     */
    fun checkBolusRecords(timeoutMs: Long = RECORD_TIMEOUT) {
        awaitValue(timeoutMs) { recordsMatch().takeIf { it } }
        if (recordsMatch()) return
        assertWithMessage("bolus records in AAPS against the boluses the pump gave, AAPS may count up to ${pump.bolusOvercount} U more")
            .that(storedBoluses()).isEqualTo(givenBoluses())
    }

    /** One record for each bolus the pump gave, in order, each at least what it gave and at most [PumpUnderTest.bolusOvercount] more. */
    private fun recordsMatch(): Boolean {
        val stored = storedBoluses()
        val given = givenBoluses()
        val over = centi(pump.bolusOvercount)
        return stored.size == given.size && stored.zip(given).all { (record, gave) -> record >= gave && record - gave <= over }
    }

    /** AAPS does not show [id]. Only a [userBolus] can raise BOLUS_DELIVERY_FAILED, a bolus of [bolus] never does. */
    fun checkNoAlarm(id: NotificationId = NotificationId.BOLUS_DELIVERY_FAILED) {
        assertWithMessage("alarm $id").that(shownNotifications()).doesNotContain(id)
    }

    /** AAPS shows [id]. The executor posts it once the queue has answered, so it may come a moment after that answer. */
    fun checkAlarm(id: NotificationId = NotificationId.BOLUS_DELIVERY_FAILED) {
        awaitValue(RECORD_TIMEOUT) { (id in shownNotifications()).takeIf { it } }
        assertWithMessage("alarm $id").that(shownNotifications()).contains(id)
    }

    private fun shownNotifications(): List<NotificationId> = testGraphs.notificationManager.notifications.value.map { it.id }

    /**
     * AAPS raised an alarm the user hears at [since] or later: a notification at [NotificationLevel.URGENT], or the
     * full-screen alarm of `UiInteraction.runAlarm`, which a driver can use for the sound while its own card stays lower
     * (Carelevo). Without a screen in the test, runAlarm posts that alarm as a system notification.
     */
    fun checkUrgentAlarmSince(since: Long) {
        fun urgent() = testGraphs.notificationManager.notifications.value.any { it.level == NotificationLevel.URGENT && it.date >= since }
        fun fullScreen() = instrumentation.targetContext.getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == AlarmNotificationManager.NOTIFICATION_ID_FULL_SCREEN && it.postTime >= since }
        // From the main thread with the app in front, runAlarm opens its alarm screen at once instead.
        fun alarmScreen(): Boolean {
            var shown = false
            onMain {
                shown = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { testGraphs.uiInteraction.errorHelperActivity.isInstance(it) }
            }
            return shown
        }
        fun alarmed() = urgent() || fullScreen() || alarmScreen()
        awaitValue(RECORD_TIMEOUT) { alarmed().takeIf { it } }
        assertWithMessage("an alarm since the pump raised it, AAPS shows ${testGraphs.notificationManager.notifications.value.map { "${it.id} ${it.level}" }}")
            .that(alarmed()).isTrue()
    }

    /** The temp basal AAPS has is the one the pump runs, or neither has one. */
    /**
     * While the pump gives no insulin, AAPS runs a 0 U/h temp basal of type PUMP_SUSPEND, and only then. Without it AAPS
     * counts basal the pump does not give, and IOB is too high.
     */
    fun checkSuspend() {
        val suspended = control.state().suspended
        val stored = runBlocking { testGraphs.persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }
        assertWithMessage("AAPS records the stop of the pump (pump stopped: $suspended, AAPS: $stored)")
            .that(stored?.type == TB.Type.PUMP_SUSPEND).isEqualTo(suspended)
        if (suspended) assertWithMessage("the stop is 0 U/h").that(stored?.rate).isEqualTo(0.0)
    }

    fun checkTempBasal() {
        val running = control.state().tempBasal
        val stored = runBlocking { testGraphs.persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }
        if (running == null) {
            assertWithMessage("AAPS has a temp basal the pump does not run").that(stored).isNull()
            return
        }
        // An AssertionError, not checkNotNull: a wrong result, which RetryRule must not retry.
        if (stored == null) throw AssertionError("The pump runs a temp basal AAPS does not have: $running")
        val pumpRate = running.absolute ?: running.percent?.toDouble()
        assertWithMessage("temp basal is absolute").that(stored.isAbsolute).isEqualTo(running.absolute != null)
        assertWithMessage("temp basal rate").that(abs(stored.rate - checkNotNull(pumpRate)) < RATE_TOLERANCE).isTrue()
    }

    /**
     * The extended bolus AAPS has is the one the pump runs, or neither has one. A pump that gives high temp basals as
     * extended boluses (the DanaR with its "Use extended boluses for >200%" setting) stores every extended bolus as one
     * that stands for a temp basal.
     */
    fun checkExtendedBolus() {
        val running = control.state().extendedBolus
        val stored = runBlocking { testGraphs.persistenceLayer.getExtendedBolusActiveAt(System.currentTimeMillis()) }
        if (running == null) {
            assertWithMessage("AAPS has an extended bolus the pump does not run").that(stored).isNull()
            return
        }
        if (stored == null) throw AssertionError("The pump runs an extended bolus AAPS does not have: $running")
        assertWithMessage("extended bolus amount").that(abs(stored.amount - running.units) < RATE_TOLERANCE).isTrue()
        assertWithMessage("extended bolus minutes").that(stored.duration).isEqualTo(running.minutes * MS_PER_MINUTE)
        assertWithMessage("the extended bolus stands for a temp basal")
            .that(stored.isEmulatingTempBasal).isEqualTo(testGraphs.activePlugin.activePumpInternal.isFakingTempsByExtendedBoluses)
    }

    /**
     * After a status read AAPS shows what the pump holds: its reservoir, its battery, and as basal the profile that runs.
     * A driver that reads a value in the wrong unit or scale shows here. Values the emulator cannot tell are not checked.
     */
    fun checkStatusShown() {
        readStatusOnNewLink()
        val state = control.state()
        val driver = testGraphs.activePlugin.activePumpInternal
        state.reservoir?.let { onPump ->
            awaitValue(RECORD_TIMEOUT) { driver.reservoirLevel.value?.cU?.takeIf { abs(it - onPump) <= RESERVOIR_TOLERANCE } }
            val shown = driver.reservoirLevel.value?.cU
                ?: throw AssertionError("AAPS shows no reservoir, the pump has $onPump U")
            assertWithMessage("reservoir AAPS shows ($shown U), the pump has $onPump U")
                .that(abs(shown - onPump) <= RESERVOIR_TOLERANCE).isTrue()
        }
        state.battery?.let { onPump ->
            awaitValue(RECORD_TIMEOUT) { driver.batteryLevel.value?.takeIf { it == onPump } }
            assertWithMessage("battery AAPS shows, the pump has $onPump %").that(driver.batteryLevel.value).isEqualTo(onPump)
        }
        state.basal?.let { onPump ->
            val profile = checkNotNull(runBlocking { testGraphs.profileFunction.getProfile() }) { "No profile runs" }
            val running = (0 until HOURS_PER_DAY).map { profile.getBasalTimeFromMidnight(it * SECONDS_PER_HOUR) }
            assertWithMessage("basal on the pump, hour by hour, against the profile AAPS runs").that(onPump.size).isEqualTo(HOURS_PER_DAY)
            onPump.zip(running).forEachIndexed { hour, (pumpRate, profileRate) ->
                assertWithMessage("basal at $hour:00, pump $pumpRate U/h, profile $profileRate U/h")
                    .that(abs(pumpRate - profileRate) <= BASAL_TOLERANCE).isTrue()
            }
        }
    }

    /** The queue closes the link when it has nothing more to do. */
    fun checkLinkClosed() {
        awaitTrue("the link closes when the queue is empty", LINK_TIMEOUT) { !control.state().linkUp }
    }

    /** A status read sends a bounded number of commands, so a driver that answers itself again and again is seen. */
    fun checkNoBurst() {
        val before = control.state().commands
        readStatus()
        // A burst can go on after the read has ended, while the link is up.
        checkLinkClosed()
        val sent = control.state().commands - before
        assertWithMessage("commands of one status read").that(sent).isAtMost(pump.maxCommandsPerStatusRead)
    }

    /**
     * The newest bolus record of AAPS has the time the bolus was given, by the phone clock, not a time off by the pump
     * clock. A record is written at the start or at the end of the bolus, so it may be up to [CLOCK_TOLERANCE_MS] off.
     */
    fun checkNewestBolusNear(givenAt: Long) {
        val newest = runBlocking { testGraphs.persistenceLayer.getBolusesFromTime(startedAt, ascending = true) }
            .filter { it.type != BS.Type.PRIMING && it.amount > 0.0 }
            .maxByOrNull { it.timestamp } ?: throw AssertionError("AAPS has no bolus record")
        val offMinutes = (newest.timestamp - givenAt) / MS_PER_MINUTE.toDouble()
        assertWithMessage("time of the bolus record, $offMinutes min from the time it was given")
            .that(abs(newest.timestamp - givenAt) <= CLOCK_TOLERANCE_MS).isTrue()
    }

    /** Sorted here: `getBolusesFromTime` gives the newest first, also with `ascending = true` (so since 3.x). */
    private fun storedBoluses(): List<Long> =
        runBlocking { testGraphs.persistenceLayer.getBolusesFromTime(startedAt, ascending = true) }
            .filter { it.type != BS.Type.PRIMING }
            // A driver that learns a bolus was never given can only set its record to 0 U: no insulin, as no record.
            .filter { it.amount > 0.0 }
            .sortedBy { it.timestamp }
            .map { centi(it.amount) }

    private fun givenBoluses(): List<Long> = control.state().boluses.map { centi(it) }

    private fun centi(units: Double): Long = (units * CENTI).roundToLong()

    // ---- Waiting ----------------------------------------------------------------------------

    /** The queue has nothing left: no command waits and none runs. */
    fun awaitQueueIdle(timeoutMs: Long = STEP_TIMEOUT) {
        awaitTrue("the command queue is idle", timeoutMs) { commandQueue.size() == 0 && commandQueue.performing() == null }
    }

    fun awaitTrue(what: String, timeoutMs: Long = STEP_TIMEOUT, condition: () -> Boolean) {
        checkNotNull(awaitValue(timeoutMs) { condition().takeIf { it } }) { "Timeout after $timeoutMs ms waiting for: $what" }
    }

    fun <T> awaitValue(timeoutMs: Long, supplier: () -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            runCatching(supplier).getOrNull()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    // ---- Seeding ----------------------------------------------------------------------------

    private fun grantPermissions() {
        val automation = instrumentation.uiAutomation
        val pkg = instrumentation.targetContext.packageName
        runCatching { automation.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT) }
        runCatching { automation.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { automation.grantRuntimePermission(pkg, Manifest.permission.POST_NOTIFICATIONS) }
    }

    private fun seedConfiguredApp() {
        val preferences = testGraphs.preferences
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        preferences.put(BooleanKey.GeneralSimpleMode, false)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_${pump.pluginName}", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
    }

    private fun seedProfile() {
        val repository = testGraphs.profileRepository
        val profile = repository.newDraft().copy(
            mgdl = true,
            ic = singleBlock(10.0),
            isf = singleBlock(50.0),
            basal = singleBlock(BASAL_RATE),
            target = singleTargetBlock(100.0, 110.0)
        )
        runBlocking { repository.add(profile) }.getOrThrow()
    }

    private fun activateProfile() {
        val store = checkNotNull(awaitValue(STEP_TIMEOUT) {
            testGraphs.profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null }
        }) { "The profile store never published $PROFILE_NAME" }
        val switch = runBlocking {
            testGraphs.profileFunction.createProfileSwitch(
                profileStore = store,
                profileName = PROFILE_NAME,
                durationInMinutes = 0,
                percentage = 100,
                timeShiftInHours = 0,
                timestamp = testGraphs.dateUtil.now(),
                action = Action.PROFILE_SWITCH,
                source = Sources.Aaps,
                listValues = emptyList(),
                iCfg = testGraphs.insulinManager.insulins.first()
            )
        }
        checkNotNull(switch) { "Could not activate $PROFILE_NAME" }
        // Only stored: it runs once a pump has it, and a pump that is not paired yet takes no profile.
        awaitTrue("the profile switch is stored") { runBlocking { testGraphs.profileFunction.getRequestedProfile() } != null }
    }

    /** The queue gives insulin only in a running mode, and a new app has none stored. */
    private fun seedRunningMode() {
        runBlocking {
            testGraphs.persistenceLayer.insertOrUpdateRunningMode(
                runningMode = RM(timestamp = testGraphs.dateUtil.now(), mode = RM.Mode.OPEN_LOOP, autoForced = false, duration = T.hours(2).msecs()),
                action = Action.CLOSED_LOOP_MODE, source = Sources.Aaps, listValues = emptyList()
            )
        }
    }

    companion object {

        const val SPEED_ARGUMENT = "emulatorSpeed"
        const val SPEED_REAL = "real"
        const val KILL_FILE = "e2e-killed-pump.bin"
        val RELOAD_HOLD = 30.seconds
        const val PROFILE_NAME = "LocalProfile1"
        const val BASAL_RATE = 0.5

        const val POLL_MS = 100L
        const val STEP_TIMEOUT = 30_000L
        const val RECORD_TIMEOUT = 10_000L
        const val LINK_TIMEOUT = 30_000L
        const val SERVICE_STOP_TIMEOUT = 10_000L

        /**
         * Long enough for a driver to fail one connect attempt and start the next one: Carelevo gives up an attempt
         * after about 21 s. A driver that scans until the queue stops it, as Equil does, makes one attempt with this
         * limit and with the real one.
         */
        const val SHORT_CONNECTION_LIMIT_S = 30L

        /** A command that runs at the test end: a status read that waits for its link, or a bolus to its end. */
        const val QUEUE_END_TIMEOUT = 60_000L

        /** A 2 U bolus at the real speed of the slowest emulated pump, with room. */
        const val BOLUS_TIMEOUT = 120_000L

        private const val CENTI = 100.0
        private const val RATE_TOLERANCE = 0.01
        /** Pumps report the reservoir to 0.01 U, some to whole units. A scale mistake is far bigger. */
        private const val RESERVOIR_TOLERANCE = 1.0
        /** A pump cuts a rate down to its own step, as Equil to 0.0125 U/h. */
        private const val BASAL_TOLERANCE = 0.0125
        private const val HOURS_PER_DAY = 24
        private const val SECONDS_PER_HOUR = 3600
        private const val PERCENT = 100.0
        /** A bolus record may be written at the end of the bolus. A pump clock off by minutes is far more. */
        private const val CLOCK_TOLERANCE_MS = 120_000L
        /**
         * A Carelevo waits 30 s between two profile updates, and one whose answer is lost times out after 30 s: skip, lost
         * try, skip, and the next try takes it.
         */
        private const val PROFILE_TIMEOUT = 180_000L
        private const val PROFILE_RETRY_MS = 5_000L
        private const val MS_PER_MINUTE = 60_000L
    }
}
