package app.aaps.e2e

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.ComposeMainActivity
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.e2e.DanaRsEmulatorUiTest.Companion.MAX_DAILY_UNITS
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.emulator.ReviewRecordCodes
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.di.DanaRSAccessors
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.testcategories.ShardA
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.Base64
import kotlin.math.abs

/**
 * Drives the **Dana-i UI** against the in-tree pump emulator, with no Bluetooth hardware and no
 * pump: `DanaRSOverviewScreen` / `DanaHistoryScreen` / `DanaUserOptionsScreen` and the insulin
 * dialog, each reaching the emulated pump through the whole production stack — UI → `CommandQueue` →
 * `DanaRSPlugin` → `DanaRSService` → `BLEComm` → [EmulatorBleTransport].
 *
 * Covers both directions, and asserts each on the far side rather than on the screen that caused it:
 * - **pump → UI**: a value seeded onto the emulator has to appear after Refresh
 * - **UI → pump**: a user option edited here, and a bolus, have to land on the emulator's `PumpState`
 *
 * The variant (which `EMULATE_DANA_*` handshake) is chosen per test in [bringUp], not `@Before`,
 * because `BleTransport` is a `@Singleton` the graph binds once — so the option has to be set before
 * the graph is built. The BLE5 test walks every screen, and is the one pass of the shared treatment
 * dialogs (bolus, temp basal, extended bolus) in the whole suite. [danaInsulinDelivery_overV1Handshake]
 * and [danaInsulinDelivery_overV3Handshake] give the same three commands through the queue with no
 * screen: what differs on those pumps is the link encryption.
 *
 * The shared machinery (seeding the configured app, navigation and uiautomator helpers) lives in
 * [AbstractDanaEmulatorUiTest]; this class supplies the Dana-i (RS) wiring, the screen legs and the
 * insulin delivery.
 *
 * ## Why this is seeded rather than wizard-driven
 * `SetupWizardE2ETest` walks the whole setup wizard because that is what it tests; it costs
 * ~140s and ends on **Virtual Pump**, so no pump-driver UI is ever rendered. This test wants the
 * pump screens, not the wizard, so it seeds that end state directly — mg/dL units, the wizard marked
 * done, an active local profile, and a paired Dana-i as the active pump — in a few seconds instead
 * of a second wizard walk. Note that what is seeded through preferences and what has to go through a
 * repository API differs; see `seedLocalProfile`.
 *
 * ## Fragility (read before editing)
 * Same rules as the other in-process E2E: selectors match **case-insensitively against text OR
 * content-desc** and match whole strings (so "Save" will not find "Save options to pump"), opens are
 * **verified-with-retry**, and it is English-only. Two traps cost real time here and are documented
 * where they bite: the pump screens are reached via Manage → Pump, *not* the bottom bar's setup
 * button ([openDanaPlugin]), and the Dana overview's action list vanishes and returns while a status
 * read is in flight, so every interaction with it waits for [waitForQueueIdle] first.
 */
@RunWith(AndroidJUnit4::class)
@ShardA
class DanaRsEmulatorUiTest : AbstractDanaEmulatorUiTest() {


    // RetryRule outermost: a flaky UI timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    private val bleTransport get() = (testGraphs.rootGraph as DanaRSAccessors).bleTransport
    private val danaRSPlugin get() = (testGraphs.rootGraph as DanaRSAccessors).danaRSPlugin

    private lateinit var emulator: EmulatorBleTransport

    // ---- pump-specific hooks --------------------------------------------------------------------


    override fun seedPairedPump(variant: ExternalOptions) {
        seedPairedDanaPump(variant)
        seedDanaAsActivePump()
    }

    override fun tearDownPump() {
        runCatching { danaRSPlugin.disconnect("test end") }
        // Disconnect first, then await: the emulator defers some responses onto their own threads
        // (v1's pairing key most of all); sendResponse drops them once disconnected, and this makes
        // sure they are actually done before the next test seeds a fresh pump.
        runCatching { if (::emulator.isInitialized) emulator.awaitPendingCallbacks() }
        // Unbind before the graph dies, or the service crashes the process.
        runCatching { danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
    }

    override fun requestStatusRead() {
        danaRSPlugin.changePump()
    }

    /**
     * Fails before the UI is involved if [seedDanaAsActivePump] did not take — a Virtual Pump here
     * would otherwise surface much later as an unhelpful "Pump management opened the wrong screen".
     *
     * Deliberately says nothing about `isInitialized`: the pump initializes itself shortly after the
     * activity launches, so its value at any given instant is a race (see [openDanaPlugin]).
     */
    override fun assertActivePumpIsThisDana() {
        val pump = activePlugin.activePumpInternal as PluginBase
        assertThat(pump).isInstanceOf(DanaRSPlugin::class.java)
        assertThat(pump.hasComposeContent()).isTrue()
    }

    private fun lastBolusAmount(): Double = emulator.pumpState.lastBolusAmount
    private fun isTempBasalRunning(): Boolean = emulator.pumpState.isTempBasalRunning
    private fun tempBasalPercent(): Int = emulator.pumpState.tempBasalPercent
    private fun isExtendedBolusRunning(): Boolean = emulator.pumpState.isExtendedBolusRunning
    private fun extendedBolusAmount(): Double = emulator.pumpState.extendedBolusAmount
    private fun deliveredHistoryCodes(): List<Int> = emulator.pumpState.historyStore.getEventsAfter(0).map { it.code }

    // ---- RS pairing seed ------------------------------------------------------------------------

    /**
     * The preferences a completed pairing leaves behind, for [variant]'s handshake.
     *
     * The device name/address/password are shared; only the encryption keys differ per handshake
     * (see [seedPairingFor]). Seeding the matching keys is also what lets the driver reconnect
     * without a PIN/password screen no test could answer.
     */
    private fun seedPairedDanaPump(variant: ExternalOptions) {
        preferences.put(DanaStringNonKey.RsName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.MacAddress, DEVICE_ADDRESS)
        preferences.put(DanaStringNonKey.Password, PASSWORD)

        emulator = bleTransport as EmulatorBleTransport
        seedPairingFor(variant)
        emulator.pairingDelayMs = 0
        emulator.writeLatencyMs = 0
        // A bolus as short as the driver allows. At the real speed (12 s/U) each 1 U bolus of the flow waited 12 s.
        emulator.speed = EmulatorSpeed.FAST
        // A value no default produces, so finding it on screen can only mean it came from the pump.
        // The emulator's own default here is 25.
        emulator.pumpState.maxDailyTotalUnits = MAX_DAILY_UNITS
        seedPumpHistory()
    }

    /** Seeds the app-side encryption keys for [variant], matching the emulator's own defaults. */
    private fun seedPairingFor(variant: ExternalOptions) {
        when (variant) {
            ExternalOptions.EMULATE_DANA_BLE5  -> {
                preferences.put(DanaStringComposedKey.Ble5PairingKey, DEVICE_NAME, value = BLE5_PAIRING_KEY)
                // Pump side of the same key — a mismatch fails the handshake rather than the assertion.
                emulator.pumpState.ble5PairingKey = BLE5_PAIRING_KEY
            }

            ExternalOptions.EMULATE_DANA_RS_V3 -> {
                val encoder = Base64.getEncoder()
                val state = emulator.pumpState
                preferences.put(DanaStringComposedKey.V3ParingKey, DEVICE_NAME, value = encoder.encodeToString(state.v3PairingKey))
                preferences.put(DanaStringComposedKey.V3RandomParingKey, DEVICE_NAME, value = encoder.encodeToString(state.v3RandomPairingKey))
                preferences.put(DanaStringComposedKey.V3RandomSyncKey, DEVICE_NAME, value = String.format("%02x", state.v3RandomSyncKey))
            }

            // v1 (ENCRYPTION_DEFAULT) pairs on the password alone — no stored keys.
            else                               -> Unit
        }
    }

    /**
     * One alarm record on the emulated pump, for [visitDanaHistory] to load.
     *
     * An alarm because the history screen opens on that type (`DanaHistoryViewModel` selects the
     * first available), so the test never has to pick a chip. Timestamped in the past because the
     * driver asks for everything *after* a "from" instant and the store compares strictly.
     */
    private fun seedPumpHistory() {
        emulator.pumpState.reviewHistoryStore.addEvent(
            code = ReviewRecordCodes.ALARM,
            timestamp = dateUtil.now() - HISTORY_RECORD_AGE_MS,
            param1 = 0,
            param2 = ReviewRecordCodes.Alarm.OCCLUSION
        )
    }

    /**
     * Makes Dana-i the active pump instead of the default Virtual Pump, the way the Config Builder
     * persists it: `ConfigBuilder_Enabled_<TYPE>_<PluginClass>`. `ConfigBuilderImpl.loadSettings`
     * reads these in `initialize()` and `verifySelectionInCategories()` then elects the single
     * enabled pump. Everything here hangs off *active*: Manage → Pump opens whichever pump that is.
     */
    private fun seedDanaAsActivePump() {
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_DanaRSPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
    }

    // ---- tests ----------------------------------------------------------------------------------

    /**
     * One test rather than several: reaching the pump screens costs a connection and a status read,
     * and @Before would repeat that per test — while the legs are ordered anyway (nothing is
     * reachable until the pump reports initialized). Each leg says what failed on its way out.
     */
    @Test
    fun danaPumpUi_readsAndWritesTheEmulatedPump() {
        bringUp(ExternalOptions.EMULATE_DANA_BLE5)
        assertActivePumpIsThisDana()
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openDanaPlugin()          // Manage → Pump → DanaRSOverviewScreen
            assertVisible("Unpair")   // the pump reads as paired from the seeded preferences

            initializePumpFromUi()    // status read against the emulator → the rest of the actions

            refreshStatusFromPump()            // Refresh → status read → pump values on screen
            visitDanaHistory()                 // Pump history → DanaHistoryScreen
            changeUserOptionsAndSaveToPump()   // User options → edit → write back to the emulator

            device.pressBack()                 // Dana screens → the main overview
            deliverInsulinFromUi()             // bolus + temp basal + extended bolus → the emulator
        } catch (t: Throwable) {
            logScreen("E2E_DANA_SCREEN")
            logPumpState("E2E_DANA_STATE")
            throw t
        } finally {
            scenario.close()
        }
    }

    /**
     * A bolus, a temp basal and an extended bolus over the RSv1 (ENCRYPTION_DEFAULT) handshake, through the
     * queue. What differs from BLE5 is the link encryption, so that is what this tests; the dialogs get their
     * one pass in the BLE5 test above.
     */
    @Test
    fun danaInsulinDelivery_overV1Handshake() = deliverOverHandshake(ExternalOptions.EMULATE_DANA_RS_V1)

    /** The same over the RSv3 (stateful randomSyncKey) handshake. */
    @Test
    fun danaInsulinDelivery_overV3Handshake() = deliverOverHandshake(ExternalOptions.EMULATE_DANA_RS_V3)

    /**
     * "Which insulin is in use" is derived from the running profile — proven here end to end, against a real
     * profile switch, a real EPS row and the real change observer that keeps the synchronous mirror fresh.
     * Every unit test mocks ProfileFunction, so this is the only place the mirror is shown to actually
     * populate; the concentration conversions behind every pump command read it non-suspending.
     *
     * Also pins the separation the catalogue rework is built on: editing the insulin *list* must not change
     * what is in use — only a profile switch does that.
     */
    @Test
    fun insulinInUse_comesFromTheRunningProfileNotTheCatalogue() {
        bringUp(ExternalOptions.EMULATE_DANA_RS_V3)
        val activated = insulinManager.insulins.first() // what activateSeededProfile() switched to

        // The mirror is refreshed off the EPS observer, so it lands shortly after the switch.
        val mirrored = awaitMirror { profileFunction.runningICfg.value }
        assertThat(mirrored).isEqualTo(activated)
        assertThat(runBlocking { profileFunction.getRunningOrRequestedICfg() }).isEqualTo(activated)

        // Appending to the catalogue leaves the in-use insulin alone (no "current entry" any more).
        val before = insulinManager.insulins.size
        val probe = insulinManager.addNewInsulin(ICfg("OnDeviceProbe", 55, 7.0, 1.0), ue = false)
        assertThat(insulinManager.insulins).hasSize(before + 1)
        assertThat(profileFunction.runningICfg.value).isEqualTo(activated)

        // …and removing it by label takes out the one asked for, not a positional guess.
        insulinManager.removeInsulin(probe.insulinLabel)
        assertThat(insulinManager.insulins).hasSize(before)
        assertThat(insulinManager.insulins.none { it.insulinNickname == "OnDeviceProbe" }).isTrue()
        assertThat(profileFunction.runningICfg.value).isEqualTo(activated)
    }

    /**
     * Waits for a value that lands asynchronously.
     *
     * The mirror is refreshed off the EPS observer, so the chain behind it is: profile switch, EPS row,
     * observer, cache invalidation, a fresh getProfile() read. On a CI box running three emulators the
     * old 10s budget was not always enough and the test failed with a null mirror, which reads as a
     * broken feature rather than a slow machine. STEP_TIMEOUT for the UI waits in this suite is 30s, so
     * use the same. A mirror that never populates still fails, just not merely because the box is busy.
     */
    private fun <T> awaitMirror(supplier: () -> T?): T? {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            supplier()?.let { return it }
            Thread.sleep(100)
        }
        return supplier()
    }

    /** Brings [variant] up with no screen, reads its status until it is ready, then delivers through the queue. */
    private fun deliverOverHandshake(variant: ExternalOptions) {
        bringUp(variant)
        assertActivePumpIsThisDana()
        try {
            initializePumpThroughQueue()
            deliverInsulinThroughQueue()
        } catch (t: Throwable) {
            logPumpState("E2E_DANA_STATE")
            throw t
        }
    }

    // ---- insulin delivery -----------------------------------------------------------------------

    /** Bolus, temp basal, extended bolus through the UI, each checked on the pump and in AAPS. */
    private fun deliverInsulinFromUi() {
        bolusFromUi()                      // Treatments → Insulin → bolus → the emulator
        applyTempBasalFromUi()             // Manage → Temp basal → the emulator (TEMP_START)
        applyExtendedBolusFromUi()         // Manage → Extended bolus → the emulator (EXTENDED_START)
        readBackDeliveredHistory()         // the same deliveries, read back off the pump as history
    }

    /** The same three through the queue, with the same checks. */
    private fun deliverInsulinThroughQueue() {
        waitForQueueIdle()
        assertThat(lastBolusAmount()).isEqualTo(0.0)
        val startedAt = System.currentTimeMillis() - CLOCK_MARGIN_MS
        runBlocking { commandQueue.bolus(DetailedBolusInfo().also { it.insulin = BOLUS_UNITS }) }
        checkBolusGivenAndRecorded(startedAt)

        assertThat(isTempBasalRunning()).isFalse()
        waitForQueueIdle()
        val profile = checkNotNull(runBlocking { profileFunction.getProfile() }) { "No profile runs" }
        runBlocking { commandQueue.tempBasalPercent(QUEUE_TEMP_BASAL_PERCENT, QUEUE_TEMP_BASAL_MINUTES, true, profile, PumpSync.TemporaryBasalType.NORMAL) }
        checkTempBasalRunsAndRecorded()

        assertThat(isExtendedBolusRunning()).isFalse()
        waitForQueueIdle()
        runBlocking { commandQueue.extendedBolus(QUEUE_EXTENDED_UNITS, QUEUE_EXTENDED_MINUTES) }
        checkExtendedBolusRunsAndRecorded()

        readBackDeliveredHistory()
    }

    /**
     * The reverse direction: every treatment just delivered is recorded on the emulated pump's APS event
     * history, so it can be read back as history — and the driver already does, on the status read each
     * following command runs (`DanaRSService.loadEvents` → `DanaRSPacketAPSHistoryEvents`). Asserted on the
     * pump's own store, the true far side. These are events the test *generated*, not seeded ones: a
     * hand-planted past bolus would carry an encoded time in the current minute that the live bolus then
     * reads as a duplicate and drops.
     */
    private fun readBackDeliveredHistory() {
        val codes = deliveredHistoryCodes()
        assertThat(codes).contains(DanaPump.HistoryEntry.BOLUS.value)
        assertThat(codes).contains(DanaPump.HistoryEntry.TEMP_START.value)
        assertThat(codes).contains(DanaPump.HistoryEntry.EXTENDED_START.value)
    }

    /**
     * The main overview → Treatments → Insulin → a `BOLUS_UNITS` bolus, confirmed and delivered to the
     * emulated pump. The path a user actually boluses through, and the most safety-critical one in the
     * app: InsulinDialog → `CommandQueue.bolus` → QueueWorker → `DanaRSPlugin.deliverTreatment` →
     * `DanaRSService` → `BLEComm` → emulator. The dialog refuses to bolus without a profile, so the
     * profile has to be real (see `activateSeededProfile`).
     */
    private fun bolusFromUi() {
        waitForQueueIdle()
        assertThat(lastBolusAmount()).isEqualTo(0.0)
        val startedAt = System.currentTimeMillis() - CLOCK_MARGIN_MS

        openVia("Treatments", expect = "Insulin")
        // The quick-add chip is formatted to the pump's bolus step (Dana-i 0.05 → "+1.00").
        openVia("Insulin", expect = BOLUS_CHIP)
        click(BOLUS_CHIP)
        // The confirm button is labelled "OK" only while no dose is set; picking one relabels it to
        // the dose itself (InsulinDialogScreen), which doubles as proof the chip registered.
        click(BOLUS_CONFIRM)
        click("OK")               // confirmation dialog → deliver
        checkBolusGivenAndRecorded(startedAt)
    }

    /**
     * Overview → Manage → Temp basal: raise the rate above 100% and commit it to the pump —
     * `setTempBasalPercent` end to end. Only reachable once the pump is initialized (Manage gates the
     * action on it), which the earlier legs establish.
     */
    private fun applyTempBasalFromUi() {
        assertThat(isTempBasalRunning()).isFalse()
        waitForQueueIdle()
        openManageAction("Temp basal")
        openViaDescContains("Temp basal", expect = DECREMENT)   // the TBR dialog (its steppers)
        // Raise the percent above 100 so the confirm button enables; the duration is pre-set to the
        // pump's step. The first plus stepper is the percent (duration is the second).
        repeat(TBR_INCREASE_TAPS) { withStaleRetry { device.findObjects(byDescContains(INCREMENT)).first().click() } }
        clickRegex("""\d+\s*%""")   // confirm button, labelled with the chosen percent
        click("OK")                 // ElementConfirmationDialog → commit
        checkTempBasalRunsAndRecorded()
    }

    /** Overview → Manage → Extended bolus: set an amount and commit it to the pump. */
    private fun applyExtendedBolusFromUi() {
        assertThat(isExtendedBolusRunning()).isFalse()
        waitForQueueIdle()
        openManageAction("Extended bolus")
        openViaDescContains("Extended bolus", expect = DECREMENT)
        // First plus stepper is the insulin amount; raise it above 0 to enable the confirm.
        repeat(EXTENDED_INCREASE_TAPS) { withStaleRetry { device.findObjects(byDescContains(INCREMENT)).first().click() } }
        clickRegex("""\d+\.\d+\s*U""")   // confirm button, labelled with the chosen amount
        click("OK")
        checkExtendedBolusRunsAndRecorded()
    }

    /** The pump gave the bolus, and AAPS stored it: a dose the pump gave but AAPS lost is IOB the loop does not see. */
    private fun checkBolusGivenAndRecorded(startedAt: Long) {
        assertThat(awaitTrue(BOLUS_TIMEOUT) { lastBolusAmount() == BOLUS_UNITS }).isTrue()
        val stored = awaitTrue(RECORD_TIMEOUT) {
            runBlocking { testGraphs.persistenceLayer.getBolusesFromTime(startedAt, ascending = true) }
                .any { abs(it.amount - BOLUS_UNITS) < UNITS_TOLERANCE }
        }
        // CI reads the log of a shard only at its end, and the tests after this one can push these lines out of it.
        // So the failure carries what the queue, the driver and the database did (CI run 42425 had nothing to read),
        // and what the emulated pump has in its history: in CI run 42438 no history read found any event, and the log
        // could not tell whether the pump had the bolus in its history or the driver lost it.
        if (!stored) {
            val pumpHistory = emulator.pumpState.historyStore.getEventsAfter(0).joinToString { "${it.code}@${it.timestamp}:${it.param1}" }
            assertWithMessage(
                "AAPS has a bolus record of $BOLUS_UNITS U. The pump history (code@time:value): [$pumpHistory], " +
                    "UTC ${emulator.pumpState.usingUTC}. The app's log:\n" + appLog(BOLUS_RECORD_EVIDENCE)
            ).fail()
        }
    }

    /** The pump runs a temp basal above 100 %, and AAPS has the same. */
    private fun checkTempBasalRunsAndRecorded() {
        assertThat(awaitTrue(COMMAND_TIMEOUT) { isTempBasalRunning() && tempBasalPercent() > 100 }).isTrue()
        waitForQueueIdle()
        val stored = awaitTrue(RECORD_TIMEOUT) {
            val tb = runBlocking { testGraphs.persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }
            tb != null && !tb.isAbsolute && tb.rate.toInt() == tempBasalPercent()
        }
        assertWithMessage("AAPS has the temp basal of ${tempBasalPercent()} %").that(stored).isTrue()
    }

    /** The pump runs an extended bolus, and AAPS has the same. */
    private fun checkExtendedBolusRunsAndRecorded() {
        assertThat(awaitTrue(COMMAND_TIMEOUT) { isExtendedBolusRunning() && extendedBolusAmount() > 0.0 }).isTrue()
        waitForQueueIdle()
        val stored = awaitTrue(RECORD_TIMEOUT) {
            val eb = runBlocking { testGraphs.persistenceLayer.getExtendedBolusActiveAt(System.currentTimeMillis()) }
            eb != null && abs(eb.amount - extendedBolusAmount()) < UNITS_TOLERANCE
        }
        assertWithMessage("AAPS has the extended bolus of ${extendedBolusAmount()} U").that(stored).isTrue()
    }

    // ---- RS-only screen legs --------------------------------------------------------------------

    /**
     * Dana overview → Refresh, which re-reads everything through the command queue
     * (`DanaOverviewViewModel.onRefreshClick`).
     *
     * The pump gets a new `REFRESHED_MAX_DAILY_UNITS` first, a value no default in the app has. Only a
     * read made after the tap can bring it to the screen, so this is the pump → UI direction end to
     * end. Refresh keeps the values of the earlier read on screen until the new ones come.
     */
    private fun refreshStatusFromPump() {
        emulator.pumpState.maxDailyTotalUnits = REFRESHED_MAX_DAILY_UNITS
        repeat(REFRESH_ATTEMPTS) {
            waitForQueueIdle()   // tap into a settled list
            click("Refresh")     // queues the read
            if (waitForVisible(REFRESHED_MAX_DAILY_UNITS_TEXT, REFRESH_TIMEOUT)) {
                waitForQueueIdle()   // let the read finish before the next step navigates
                return
            }
            cancelStrayUnpairDialog()
        }
        error("Refresh never brought '$REFRESHED_MAX_DAILY_UNITS_TEXT' from the pump")
    }

    /**
     * Undoes a Refresh tap that landed on Unpair — a belt-and-braces guard now that [waitForQueueIdle]
     * settles the list first.
     *
     * The overview's actions are `visible = isInitialized`, so a tap that lands mid-read (the list
     * collapsing as the pump resets) can hit Unpair's coordinates instead and open this dialog.
     * Cancel it and let the caller retry — going through with an unpair would strip the seeded
     * pairing and fail everything after it for an unrelated reason.
     */
    private fun cancelStrayUnpairDialog() {
        if (device.findObject(byText("Reset pairing information?")) != null) {
            withStaleRetry { find("Cancel").click() }
            device.waitForIdle(IDLE_MS)
        }
    }

    /**
     * Overview → Pump history → Refresh, which loads the type's records off the pump and renders
     * them (`REVIEW__ALARM` here → `DanaRSPacketHistory` → `DanaHistoryRecordDao` → the screen).
     *
     * Opening asserts on the "Alarms" chip rather than the screen's "Refresh" button: the Dana
     * overview has a "Refresh" of its own, so a tap that never landed would still satisfy it.
     * Then the [seedPumpHistory] record has to arrive — the screen starts on "No records found",
     * so this fails if the load returns nothing, which is what it did before the emulator learned
     * to serve the review-history commands.
     */
    private fun visitDanaHistory() {
        waitForQueueIdle()   // overview settled before leaving it
        openVia("Pump history", expect = "Alarms")
        openVia("Refresh", expect = HISTORY_ALARM_TEXT)
        returnToDanaOverview()
    }

    /**
     * Overview → User options: nudge "LCD on time" up one step and save it **to the pump**, then
     * back to the overview.
     *
     * The only leg of this test that writes. Everything else reads, and a read can pass against a
     * driver that quietly drops what the UI hands it — so this asserts the new value on the
     * emulator's own [PumpState], not on the screen that produced it: UI → command queue →
     * `DanaRSPlugin` → `DanaRSService` → `BLEComm` → emulator, end to end.
     */
    private fun changeUserOptionsAndSaveToPump() {
        waitForQueueIdle()   // overview settled before leaving it
        openVia("User options", expect = SAVE_USER_OPTIONS)

        val before = emulator.pumpState.lcdOnTimeSec
        // Three plus steppers are on this screen (LCD on time, Backlight on time, Shutdown); they are
        // laid out in that order, so the first is LCD's. Its range is 5-240, so one step up is always
        // in bounds.
        withStaleRetry { device.findObjects(byDescContains(INCREMENT)).first().click() }
        click(SAVE_USER_OPTIONS)

        // Asserts only that it grew, not by how much: the step is the screen's business (5 today),
        // and pinning it here would fail this test for a UI tweak that broke nothing.
        val written = awaitTrue(SAVE_TIMEOUT) { emulator.pumpState.lcdOnTimeSec > before }
        assertThat(written).isTrue()
        // The pump has the value before the save leaves the queue, and the screen keeps redrawing until it does.
        waitForQueueIdle()

        // Saving may leave the screen on its own; only tap Back if it did not.
        if (!waitForVisible("Unpair", IDLE_MS)) returnToDanaOverview()
    }

    /**
     * Sub-screens return to DanaScreen.OVERVIEW via their **toolbar** back arrow.
     *
     * Not `device.pressBack()`: system back pops the whole PluginContent route off the NavHost and
     * lands on the AAPS overview, one screen too far — the Dana screens switch between themselves
     * with their own state, inside a single destination.
     */
    private fun returnToDanaOverview() {
        openVia("Back", expect = "Unpair")
    }

    /** The exact inputs ComposeMainActivity's `showPumpSetup` reads, sampled at failure time. */
    private fun logPumpState(tag: String) {
        runCatching {
            val pump = activePlugin.activePumpInternal as PluginBase
            android.util.Log.e(
                tag,
                "activePump=${pump.javaClass.simpleName} enabled=${pump.isEnabled()} " +
                    "composeContent=${pump.hasComposeContent()} initialized=${activePlugin.activePump.isInitialized()} " +
                    "suspended=${activePlugin.activePump.isSuspended()}"
            )
        }
        runCatching {
            val s = emulator.pumpState
            android.util.Log.e(
                tag,
                "emulator lcdOnTimeSec=${s.lcdOnTimeSec} backlightOnTimeSec=${s.backlightOnTimeSec} " +
                    "shutdownHour=${s.shutdownHour} beepAndAlarm=${s.beepAndAlarm}"
            )
        }
    }

    companion object {

        /** Selectors match whole strings, so this cannot be shortened to "Save". */
        private const val SAVE_USER_OPTIONS = "Save options to pump"

        // Mirrors BLECommBLE5IntegrationTest / DanaRsEmulatorPumpTest.
        private const val DEVICE_NAME = "UHH00002TI"
        private const val DEVICE_ADDRESS = "00:00:00:00:00:00"
        private const val BLE5_PAIRING_KEY = "474632"
        private const val PASSWORD = "0000"

        /** Seeded onto the emulator; the app's own defaults never produce it. */
        private const val MAX_DAILY_UNITS = 42.0

        /** Set on the emulator just before Refresh; only a read after the tap shows it. */
        private const val REFRESHED_MAX_DAILY_UNITS = 43.0
        private const val REFRESHED_MAX_DAILY_UNITS_TEXT = "43.00 U"

        /** The alarm seeded onto the pump, as DanaRSPacketHistory names it for the screen. */
        private const val HISTORY_ALARM_TEXT = "Occlusion"
        private const val HISTORY_RECORD_AGE_MS = 60 * 60 * 1000L

        private const val REFRESH_TIMEOUT = 30_000L
        private const val REFRESH_ATTEMPTS = 3
        private const val SAVE_TIMEOUT = 30_000L

        private const val BOLUS_UNITS = 1.0
        /** The insulin dialog's quick-add chip and confirm button for `BOLUS_UNITS` at the Dana-i bolus step. */
        private const val BOLUS_CHIP = "+1.00"
        private const val BOLUS_CONFIRM = "1.00 U"
        private const val BOLUS_TIMEOUT = 60_000L
        private const val COMMAND_TIMEOUT = 60_000L
        private const val TBR_INCREASE_TAPS = 3
        private const val EXTENDED_INCREASE_TAPS = 3
        private const val QUEUE_TEMP_BASAL_PERCENT = 150
        private const val QUEUE_TEMP_BASAL_MINUTES = 60
        private const val QUEUE_EXTENDED_UNITS = 1.0
        private const val QUEUE_EXTENDED_MINUTES = 30

        // A Dana driver stores a bolus only after it has read the pump history, which follows the end of the
        // bolus. So the record can come well after the pump is done.
        private const val RECORD_TIMEOUT = 60_000L
        private const val UNITS_TOLERANCE = 0.001
        /** The pump writes whole seconds, so a record can start a little before the test asked. */
        private const val CLOCK_MARGIN_MS = 60_000L

        /** How a bolus gets its record: the queue, the bolus end, the history read and its BOLUS event, a refused record. */
        private val BOLUS_RECORD_EVIDENCE =
            Regex("PUMPQUEUE|DELIVERY_COMPLETE|Delivery complete|Loading event history|Last record|Events loaded|Timeout waiting for history|EVENT |Inserted Bolus|Ignoring|WRONG_PUMP|Reply not received|Disconnect")
    }
}
