package app.aaps.e2e

import android.Manifest
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.emulator.EmulatorRfcommTransport
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.testcategories.ShardB
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import javax.inject.Provider
import org.junit.After
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Drives the **DanaR family drivers** against the in-tree pump emulator, with no Bluetooth hardware:
 * `DanaRPlugin` / `DanaRKoreanPlugin` / `DanaRv2Plugin` → their execution service →
 * `EmulatorRfcommTransport`.
 *
 * The RFCOMM counterpart to [DanaRsEmulatorPumpTest]: three separate drivers here, rather than one
 * driver with three handshakes, so each variant gets its own connect. What they share is the reason
 * this cannot be a JVM test — an execution service is a dagger-android `DaggerService` that only
 * exists on a device (see [DanaRsEmulatorPumpTest] for the full argument).
 *
 * `DanaModules.provideRfcommTransport` picks the emulator, and the variant *also* decides which
 * plugin it auto-enables — so unlike the RS side, choosing the option here chooses the driver.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardB
class DanaREmulatorPumpTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var config: Config
    // A Provider, not the transport directly: provideRfcommTransport enables the target plugin
    // (storeSettings), which needs pluginStore.plugins - set only after hiltRule.inject(). Resolving it
    // lazily (after connect) returns the @Singleton the execution service already built by then.
    @Inject lateinit var rfcommTransportProvider: Provider<RfcommTransport>
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private var activePlugin: PluginBase? = null

    @Test
    fun danaR_connectsAgainstEmulator() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R) { danaRPlugin }
    }

    @Test
    fun danaRKorean_connectsAgainstEmulator() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R_KOREAN) { danaRKoreanPlugin }
    }

    @Test
    fun danaRv2_connectsAgainstEmulator() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R_V2) { danaRv2Plugin }
    }

    /**
     * DanaRv2 (the most command-capable variant) drives a temp basal, an extended bolus and a bolus to
     * the emulator; each must land on the emulated pump's state. DanaRv2 is used because it supports all
     * three - the connect tests above already cover the other variants' handshakes.
     *
     * Commands run through the execution service on its own thread, so assertions poll the emulator
     * state rather than reading it immediately after the call returns.
     */
    @Test
    fun pumpCommands_reachTheEmulator() {
        val pump = bringUpConnected(ExternalOptions.EMULATE_DANA_R_V2) { danaRv2Plugin }
        val state = (rfcommTransportProvider.get() as EmulatorRfcommTransport).emulator.state

        runBlocking {
            pump.setTempBasalPercent(TBR_PERCENT, TBR_DURATION_MIN, enforceNew = true, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(awaitTrue(COMMAND_TIMEOUT) { state.isTempBasalRunning && state.tempBasalPercent == TBR_PERCENT }).isTrue()

        runBlocking { pump.setExtendedBolus(EXTENDED_UNITS, EXTENDED_DURATION_MIN) }
        assertThat(awaitTrue(COMMAND_TIMEOUT) {
            state.isExtendedBolusRunning && state.extendedBolusAmount in (EXTENDED_UNITS - 0.001)..(EXTENDED_UNITS + 0.001)
        }).isTrue()

        runBlocking { pump.deliverTreatment(DetailedBolusInfo().also { it.insulin = BOLUS_UNITS }) }
        assertThat(awaitTrue(COMMAND_TIMEOUT) {
            state.lastBolusAmount in (BOLUS_UNITS - 0.001)..(BOLUS_UNITS + 0.001)
        }).isTrue()
    }

    /**
     * The plain **DanaR** and **DanaRKorean** variants run their own execution services
     * (`DanaRExecutionService` / `DanaRKoreanExecutionService`), which a bare connect leaves at ~7-11%.
     * These drive the two commands both variants support - a temp basal and a bolus (neither has
     * DanaRv2's extended bolus) - through those services and read them back off the emulated pump.
     */
    @Test
    fun danaR_deliversTempBasalAndBolus() {
        deliverTempBasalAndBolus(bringUpConnected(ExternalOptions.EMULATE_DANA_R) { danaRPlugin })
    }

    @Test
    fun danaRKorean_deliversTempBasalAndBolus() {
        deliverTempBasalAndBolus(bringUpConnected(ExternalOptions.EMULATE_DANA_R_KOREAN) { danaRKoreanPlugin })
    }

    private fun deliverTempBasalAndBolus(pump: Pump) {
        val state = (rfcommTransportProvider.get() as EmulatorRfcommTransport).emulator.state

        runBlocking {
            pump.setTempBasalPercent(TBR_PERCENT, TBR_DURATION_MIN, enforceNew = true, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(awaitTrue(COMMAND_TIMEOUT) { state.isTempBasalRunning && state.tempBasalPercent == TBR_PERCENT }).isTrue()

        runBlocking { pump.deliverTreatment(DetailedBolusInfo().also { it.insulin = BOLUS_UNITS }) }
        assertThat(awaitTrue(COMMAND_TIMEOUT) {
            state.lastBolusAmount in (BOLUS_UNITS - 0.001)..(BOLUS_UNITS + 0.001)
        }).isTrue()
    }

    /**
     * Reads the DanaRv2 **review** history behind the Pump-history screen (`loadHistory` →
     * `MsgHistoryBolus` → `MsgHistoryAll`), which the delivery tests never touch. Seeds one bolus on the
     * emulator's review store and reads it back, so `MsgHistoryAll`'s bolus branch parses a real record
     * and the streaming/done handshake (records on 0x3101, then a 0x31F1 `MsgHistoryDone`) is exercised.
     * Increment 1 of the review-history coverage - bolus first; the other record types follow.
     */
    @Test
    fun danaRv2_readsReviewBolusHistory() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R_V2) { danaRv2Plugin }
        val emulatorState = (rfcommTransportProvider.get() as EmulatorRfcommTransport).emulator.state
        // Minute-aligned (seconds=0) so MsgHistoryAll's bolus branch reads byte 6 as duration 0; param2
        // 0x80 is the standard-bolus sub-code, param1 the amount in hundredths (150 = 1.50 U).
        val timestamp = System.currentTimeMillis() / 60_000L * 60_000L - 2 * 60 * 60 * 1000L
        emulatorState.reviewHistoryStore.addEvent(RecordTypes.RECORD_TYPE_BOLUS.toInt(), timestamp, 150, 0x80)

        // Drive the read directly through the plugin, off-thread (loadHistory blocks on an uncancellable
        // sleep-loop until the done arrives). The emulator counter proves the read reached the pump; the DB
        // poll proves the driver parsed the record back; historyDoneReceived is checked last. A broken step
        // fails on its own assertion instead of wedging the shard.
        Thread { runCatching { danaRv2Plugin.loadHistory(RecordTypes.RECORD_TYPE_BOLUS) } }.start()

        assertThat(awaitTrue(HISTORY_TIMEOUT_MS) { emulatorState.bolusHistoryRequestCount > 0 }).isTrue()
        assertThat(
            awaitTrue(HISTORY_TIMEOUT_MS) {
                danaHistoryRecordDao.allFromByType(timestamp, RecordTypes.RECORD_TYPE_BOLUS).blockingGet()
                    .any { it.timestamp == timestamp }
            }
        ).isTrue()
        val record = danaHistoryRecordDao.allFromByType(timestamp, RecordTypes.RECORD_TYPE_BOLUS)
            .blockingGet().first { it.timestamp == timestamp }
        assertThat(record.value).isWithin(0.001).of(1.5)
        assertThat(record.bolusType).isEqualTo("S")
        assertThat(danaPump.historyDoneReceived).isTrue() // the 0x31F1 MsgHistoryDone should end the stream
    }

    /**
     * Reads back the other DanaRv2 **review** record types (alarm, glucose, carbs, suspend, refill), each
     * a distinct `MsgHistoryAll` decode branch (alarm string, raw vs hundredths values, on/off state) that
     * bolus alone doesn't reach. Increment 2 - the emulator now serves every per-type review opcode.
     */
    @Test
    fun danaRv2_readsReviewHistoryTypes() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R_V2) { danaRv2Plugin }
        val store = (rfcommTransportProvider.get() as EmulatorRfcommTransport).emulator.state.reviewHistoryStore
        // Distinct minutes, 3h back so they don't collide with the bolus test's record.
        val base = System.currentTimeMillis() / 60_000L * 60_000L - 3 * 60 * 60 * 1000L
        val alarmTs = base + 60_000; val glucoseTs = base + 120_000; val carboTs = base + 180_000
        val suspendTs = base + 240_000; val refillTs = base + 300_000
        store.addEvent(RecordTypes.RECORD_TYPE_ALARM.toInt(), alarmTs, 0, 79)     // param2 79 = Occlusion
        store.addEvent(RecordTypes.RECORD_TYPE_GLUCOSE.toInt(), glucoseTs, 120, 0) // raw value 120
        store.addEvent(RecordTypes.RECORD_TYPE_CARBO.toInt(), carboTs, 30, 0)      // raw value 30
        store.addEvent(RecordTypes.RECORD_TYPE_SUSPEND.toInt(), suspendTs, 0, 79)  // param2 79 = On
        store.addEvent(RecordTypes.RECORD_TYPE_REFILL.toInt(), refillTs, 300, 0)   // 3.00 U (hundredths)

        readReviewType(RecordTypes.RECORD_TYPE_ALARM, alarmTs)
        readReviewType(RecordTypes.RECORD_TYPE_GLUCOSE, glucoseTs)
        readReviewType(RecordTypes.RECORD_TYPE_CARBO, carboTs)
        readReviewType(RecordTypes.RECORD_TYPE_SUSPEND, suspendTs)
        readReviewType(RecordTypes.RECORD_TYPE_REFILL, refillTs)

        assertThat(recordAt(RecordTypes.RECORD_TYPE_ALARM, alarmTs).alarm).isEqualTo("Occlusion")
        assertThat(recordAt(RecordTypes.RECORD_TYPE_GLUCOSE, glucoseTs).value).isWithin(0.001).of(120.0)
        assertThat(recordAt(RecordTypes.RECORD_TYPE_CARBO, carboTs).value).isWithin(0.001).of(30.0)
        assertThat(recordAt(RecordTypes.RECORD_TYPE_SUSPEND, suspendTs).stringValue).isEqualTo("On")
        assertThat(recordAt(RecordTypes.RECORD_TYPE_REFILL, refillTs).value).isWithin(0.001).of(3.0)
    }

    /** Reads one review [type] and waits (bounded) for the driver to parse the seeded record at [timestamp]. */
    private fun readReviewType(type: Byte, timestamp: Long) {
        val t = Thread { runCatching { danaRv2Plugin.loadHistory(type) } }
        t.start()
        t.join(HISTORY_TIMEOUT_MS) // loadHistory returns when the 0x31F1 done arrives; bound it per type
        assertThat(
            awaitTrue(HISTORY_TIMEOUT_MS) {
                danaHistoryRecordDao.allFromByType(timestamp, type).blockingGet().any { it.timestamp == timestamp }
            }
        ).isTrue()
    }

    private fun recordAt(type: Byte, timestamp: Long) =
        danaHistoryRecordDao.allFromByType(timestamp, type).blockingGet().first { it.timestamp == timestamp }

    /**
     * Brings [plugin] up against the emulated [variant] and requires it to connect.
     *
     * Per test rather than in `@Before`: `RfcommTransport` is `@Singleton` and reads
     * `config.isEnabled` once, when the graph first constructs it, so the variant has to be chosen
     * before `hiltRule.inject()`. Each test method gets a fresh Hilt component, and so a fresh pump.
     */
    private fun bringUpConnected(variant: ExternalOptions, plugin: () -> PluginBase): Pump {
        EmulatedOptions.enabled = setOf(variant)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)

        // The emulated pump answers as this device, and the driver looks it up by name:
        // AbstractDanaRExecutionService.connect asks the transport for a socket for RName. Both are
        // set because DanaModules generates a random name when EmulatorDeviceName is empty, and
        // isConfigured() is false while RName is.
        preferences.put(DanaStringNonKey.EmulatorDeviceName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.RName, DEVICE_NAME)

        // The plugin/config init MainApp.onCreate does, which the Hilt test app doesn't.
        pluginStore.plugins = pluginList
        config.initCompleted()

        val pump = plugin()
        activePlugin = pump
        // Runs onStart inline (unlike setPluginEnabled, which launches it on pluginScope), and
        // onStart is what binds the execution service. The service injects RfcommTransport, which
        // is what finally constructs the emulator — after the preferences above, deliberately.
        pump.setPluginEnabledBlocking(PluginType.PUMP, true)
        assertThat(pump.isEnabled()).isTrue()

        val asPump = pump as Pump
        assertThat(asPump.isConfigured()).isTrue()

        // bindService is async, so connect() is a no-op until the service lands — drive it until it
        // takes. Keeping the whole service lifetime inside this component's is also what stops it
        // outliving the test and crashing on a torn-down component (see DanaRsEmulatorPumpTest).
        // Only (re)initiate a connect while neither connected nor connecting. DanaR's connect() spawns a
        // fresh RFCOMM connection thread on every call, so re-calling it once already connected would
        // tear the socket down and re-establish it right as commands start (unlike RS's idempotent BLE
        // connect). Polling isConnected() still lets the async service bind land.
        val connected = awaitTrue(CONNECT_TIMEOUT) {
            if (!asPump.isConnected() && !asPump.isConnecting()) asPump.connect("e2e")
            asPump.isConnected()
        }
        assertThat(connected).isTrue()
        return asPump
    }

    @After
    fun tearDown() {
        // Drain queued work before anything else: left running it keeps talking to the pump and
        // posts notifications into whichever test comes next, whose UI then recomposes under
        // uiautomator (SetupWizardE2EHiltTest died that way, CI build 40253).
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { (activePlugin as? Pump)?.disconnect("test end") }
        // Unbind before the component dies — onStop calls unbindService.
        runCatching { activePlugin?.setPluginEnabledBlocking(PluginType.PUMP, false) }
        activePlugin = null
        // SharedPreferences outlive the Hilt component, so don't leave sibling tests in this
        // process believing a DanaR is configured.
        runCatching {
            preferences.remove(DanaStringNonKey.RName)
            preferences.remove(DanaStringNonKey.EmulatorDeviceName)
        }
        EmulatedOptions.enabled = emptySet()
    }

    /** Polls [condition] until it returns true or [timeoutMs] elapses. */
    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    companion object {

        private const val PKG = "info.nightscout.androidaps"

        /** Shaped like the name DanaModules generates for an emulated DanaR ("DAN#####EM"). */
        private const val DEVICE_NAME = "DAN00001EM"
        private const val CONNECT_TIMEOUT = 40_000L
        private const val COMMAND_TIMEOUT = 30_000L
        private const val HISTORY_TIMEOUT_MS = 30_000L
        private const val POLL_MS = 250L

        private const val TBR_PERCENT = 150
        private const val TBR_DURATION_MIN = 60
        private const val EXTENDED_UNITS = 1.0
        private const val EXTENDED_DURATION_MIN = 60
        private const val BOLUS_UNITS = 1.0
    }
}
