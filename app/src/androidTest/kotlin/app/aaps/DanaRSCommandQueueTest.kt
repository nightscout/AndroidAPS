package app.aaps

import android.Manifest
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.implementation.plugin.PluginStore
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Full integration test for DanaRS pump using the emulated BLE transport.
 *
 * Tests the complete stack: CommandQueue → DanaRSPlugin → DanaRSService → BLEComm → EmulatorBleTransport.
 * Uses the real app DI graph with EmulatorBleTransport replacing real Bluetooth.
 */
class DanaRSCommandQueueTest @Inject constructor() {

    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var emulatorTransport: EmulatorBleTransport
    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pumpSync: PumpSync

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()
    private val deviceName = "DanaRS_Emulator"
    private val deviceAddress = "00:11:22:33:44:55"

    private val profileJSON = """{"dia":"5","carbratio":[{"time":"00:00","value":"30"}],"carbs_hr":"20","delay":"20","sens":[{"time":"00:00","value":"3"},{"time":"2:00","value":"3.4"}],"timezone":"UTC","basal":[{"time":"00:00","value":"1"}],"target_low":[{"time":"00:00","value":"4.5"}],"target_high":[{"time":"00:00","value":"7"}],"startDate":"1970-01-01T00:00:00.000Z","units":"mmol"}"""

    @Before
    fun setup() {
        context.androidInjector().inject(this)
        runBlocking { persistenceLayer.clearDatabases() }

        // Configure DanaRS preferences via SharedPreferences
        sharedPreferences.edit()
            .putString("danars_name", deviceName)
            .putString("danars_address", deviceAddress)
            .putString("danars_password", "0000")
            .putString("danars_pairing_key_$deviceName", "ABCD")
            .commit()

        // Configure emulator state
        emulatorTransport.pumpState.reservoirRemainingUnits = 150.0
        emulatorTransport.pumpState.batteryRemaining = 90
        emulatorTransport.pumpState.currentBasal = 1.0

        // Switch active pump to DanaRS
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        pluginStore.plugins
            .filter { it is VirtualPump }
            .forEach { it.setPluginEnabled(PluginType.PUMP, false) }
        configBuilder.initialize()
        assertThat(activePlugin.activePump).isInstanceOf(DanaRSPlugin::class.java)

        // Start plugin — binds DanaRSService and queues initial readStatus via changePump()
        danaRSPlugin.onStart()

        // Wait for the initial readStatus (queued by changePump()) to complete.
        // This ensures: service is bound, pump is connected, handshake done, status read.
        assertThat(waitUntil(60) { danaRSPlugin.isInitialized() })
            .isTrue()
    }

    @After
    fun tearDown() {
        danaRSPlugin.onStop()
        rxHelper.clear()
    }

    // ========== Status Tests ==========

    @Test
    fun initialReadStatus_populatesPumpState() {
        // The setup already ran readStatus via changePump().
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.1).of(150.0)
        assertThat(danaPump.batteryRemaining).isEqualTo(90)
        assertThat(danaPump.currentBasal).isWithin(0.01).of(1.0)
        assertThat(danaPump.lastConnection).isGreaterThan(0)
        assertThat(danaPump.serialNumber).isNotEmpty()
    }

    @Test
    fun readStatus_throughCommandQueue() {
        // Change emulator state after initial read
        emulatorTransport.pumpState.reservoirRemainingUnits = 42.5
        emulatorTransport.pumpState.batteryRemaining = 25
        emulatorTransport.pumpState.currentBasal = 0.5
        emulatorTransport.pumpState.dailyTotalUnits = 12.3

        waitForQueueIdle(30)

        val result = awaitCommand(60) { callback ->
            commandQueue.readStatus("emulator state test", callback)
        }
        assertThat(result.success).isTrue()

        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.1).of(42.5)
        assertThat(danaPump.batteryRemaining).isEqualTo(25)
        assertThat(danaPump.currentBasal).isWithin(0.01).of(0.5)
        assertThat(danaPump.dailyTotalUnits).isWithin(0.1).of(12.3)
    }

    @Test
    fun pluginIsInitialized_afterSetup() {
        assertThat(danaRSPlugin.isInitialized()).isTrue()
    }

    // ========== Bolus Tests ==========

    @Test
    fun bolus_deliversInsulin() {
        waitForQueueIdle(30)

        val detailedBolusInfo = DetailedBolusInfo().apply {
            insulin = 0.1
        }

        val result = awaitCommand(60) { callback ->
            commandQueue.bolus(detailedBolusInfo, callback)
        }
        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(0.05).of(0.1)

        // Emulator state should reflect the bolus
        assertThat(emulatorTransport.pumpState.lastBolusAmount).isWithin(0.05).of(0.1)
    }

    // ========== Extended Bolus Tests ==========

    @Test
    fun extendedBolus_setAndCancel() {
        waitForQueueIdle(30)

        // Set extended bolus: 1.0U over 30 minutes
        val setResult = awaitCommand(60) { callback ->
            commandQueue.extendedBolus(1.0, 30, callback)
        }
        assertThat(setResult.success).isTrue()

        // Emulator should have extended bolus state set
        assertThat(emulatorTransport.pumpState.extendedBolusAmount).isWithin(0.1).of(1.0)

        waitForQueueIdle(30)

        // Cancel extended bolus
        val cancelResult = awaitCommand(60) { callback ->
            commandQueue.cancelExtended(callback)
        }
        assertThat(cancelResult.success).isTrue()
    }

    // ========== Temp Basal Tests ==========

    @Test
    fun tempBasalPercent_setAndCancel() {
        waitForQueueIdle(30)

        val profile = createTestProfile()

        // Set temp basal: 150% for 1 hour
        val setResult = awaitCommand(60) { callback ->
            commandQueue.tempBasalPercent(150, 60, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
        }
        assertThat(setResult.success).isTrue()

        // Emulator should have temp basal state set
        assertThat(emulatorTransport.pumpState.tempBasalPercent).isGreaterThan(100)

        waitForQueueIdle(30)

        // Cancel temp basal
        val cancelResult = awaitCommand(60) { callback ->
            commandQueue.cancelTempBasal(true, callback = callback)
        }
        assertThat(cancelResult.success).isTrue()
    }

    // ========== Load Events Tests ==========

    @Test
    fun loadEvents_completesSuccessfully() {
        waitForQueueIdle(30)

        val result = awaitCommand(60) { callback ->
            commandQueue.loadEvents(callback)
        }
        assertThat(result.success).isTrue()
    }

    // ========== User Settings Tests ==========

    @Test
    fun setUserOptions_writesSettingsToPump() {
        waitForQueueIdle(30)

        // Change DanaPump user option fields (these get sent to the pump)
        danaPump.beepAndAlarm = 3
        danaPump.lcdOnTimeSec = 15
        danaPump.backlightOnTimeSec = 10
        danaPump.units = 1 // mg/dL

        val result = awaitCommand(60) { callback ->
            commandQueue.setUserOptions(callback)
        }
        assertThat(result.success).isTrue()

        // Verify emulator received the settings
        assertThat(emulatorTransport.pumpState.beepAndAlarm).isEqualTo(3)
        assertThat(emulatorTransport.pumpState.lcdOnTimeSec).isEqualTo(15)
        assertThat(emulatorTransport.pumpState.backlightOnTimeSec).isEqualTo(10)
        assertThat(emulatorTransport.pumpState.units).isEqualTo(1)
    }

    // ========== Multi-Command Sequence Tests ==========

    @Test
    fun multipleCommandsInSequence() {
        waitForQueueIdle(30)

        // Queue bolus + readStatus back-to-back
        // Change emulator state so readStatus has something new to verify
        emulatorTransport.pumpState.batteryRemaining = 55

        val detailedBolusInfo = DetailedBolusInfo().apply {
            insulin = 0.1
        }

        val bolusResult = awaitCommand(60) { callback ->
            commandQueue.bolus(detailedBolusInfo, callback)
        }
        assertThat(bolusResult.success).isTrue()
        assertThat(bolusResult.bolusDelivered).isWithin(0.05).of(0.1)

        waitForQueueIdle(30)

        val statusResult = awaitCommand(60) { callback ->
            commandQueue.readStatus("post-bolus check", callback)
        }
        assertThat(statusResult.success).isTrue()
        assertThat(danaPump.batteryRemaining).isEqualTo(55)

        // Reservoir should reflect the bolus (150 - 0.1 = 149.9)
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.2).of(149.9)
    }

    @Test
    fun bolusReducesReservoir() {
        waitForQueueIdle(30)

        val reservoirBefore = emulatorTransport.pumpState.reservoirRemainingUnits

        val detailedBolusInfo = DetailedBolusInfo().apply {
            insulin = 0.5
        }

        val result = awaitCommand(60) { callback ->
            commandQueue.bolus(detailedBolusInfo, callback)
        }
        assertThat(result.success).isTrue()

        // Emulator should have deducted insulin from reservoir
        assertThat(emulatorTransport.pumpState.reservoirRemainingUnits)
            .isWithin(0.05).of(reservoirBefore - 0.5)

        // Read status to sync DanaPump state
        waitForQueueIdle(30)
        val statusResult = awaitCommand(60) { callback ->
            commandQueue.readStatus("verify reservoir", callback)
        }
        assertThat(statusResult.success).isTrue()
        assertThat(danaPump.reservoirRemainingUnits)
            .isWithin(0.2).of(reservoirBefore - 0.5)
    }

    // ========== Helpers ==========

    private fun createTestProfile(): ProfileSealed.Pure {
        val pureProfile = pureProfileFromJson(JSONObject(profileJSON), dateUtil)
            ?: error("Failed to create test profile from JSON")
        return ProfileSealed.Pure(pureProfile, activePlugin)
    }

    private fun awaitCommand(maxSeconds: Long, block: (Callback) -> Unit): PumpEnactResult {
        val latch = CountDownLatch(1)
        var pumpResult: PumpEnactResult? = null
        val callback = object : Callback() {
            override fun run() {
                pumpResult = result
                latch.countDown()
            }
        }
        block(callback)
        assertThat(latch.await(maxSeconds, TimeUnit.SECONDS)).isTrue()
        return pumpResult!!
    }

    private fun waitForQueueIdle(maxSeconds: Int) {
        assertThat(waitUntil(maxSeconds) {
            commandQueue.size() == 0
                && !commandQueue.isRunning(Command.CommandType.READSTATUS)
                && !commandQueue.isRunning(Command.CommandType.BOLUS)
                && !commandQueue.isRunning(Command.CommandType.TEMPBASAL)
                && !commandQueue.isRunning(Command.CommandType.EXTENDEDBOLUS)
                && !commandQueue.isRunning(Command.CommandType.LOAD_EVENTS)
        }).isTrue()
    }

    private fun waitUntil(maxSeconds: Int, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > maxSeconds * 1000L) return false
            Thread.sleep(100)
        }
        return true
    }
}
