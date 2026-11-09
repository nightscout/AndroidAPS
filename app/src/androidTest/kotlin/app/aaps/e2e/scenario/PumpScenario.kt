package app.aaps.e2e.scenario

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.notifications.NotificationId
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
    private var startedAt = 0L

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
        awaitTrue("the profile runs") { runBlocking { testGraphs.profileFunction.getProfile() } != null }
        awaitQueueIdle()
    }

    fun unpair() = pump.unpair(this)

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

    fun cancelTempBasal(): PumpEnactResult = queued { commandQueue.cancelTempBasal(enforceNew = true) }

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

    /** The temp basal AAPS has is the one the pump runs, or neither has one. */
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

    /** The extended bolus AAPS has is the one the pump runs, or neither has one. */
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
        assertWithMessage("a real extended bolus, not one that stands for a temp basal").that(stored.isEmulatingTempBasal).isFalse()
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
        private const val MS_PER_MINUTE = 60_000L
    }
}
