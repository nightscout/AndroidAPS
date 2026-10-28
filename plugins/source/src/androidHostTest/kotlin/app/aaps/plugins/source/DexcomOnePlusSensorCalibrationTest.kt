package app.aaps.plugins.source

import android.content.Context
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.source.SensorCalibrationResult
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverReal
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.session.OnePlusBleSession
import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationQueue
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The switch is on by default. While it is off, [DexcomOnePlusPlugin.calibrateSensor] returns
 * [SensorCalibrationResult.NotSupported] and the production queue stays empty, so the Control loop
 * has nothing from which to build opcode 0x34. While it is on, every check of
 * `OnePlusCalibrationGate` must pass before anything is queued, and only one value can wait.
 *
 * The session is forced up on purpose: a missing check would queue the value. The assertion is
 * that the queue is still empty.
 */
class DexcomOnePlusSensorCalibrationTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private val notificationManager: NotificationManager = mock()
    private val activePlugin: ActivePlugin = mock<ActivePlugin>().also {
        whenever(it.activeCalibration).thenReturn(mock<Calibration>())
    }

    private lateinit var plugin: DexcomOnePlusPlugin

    @BeforeEach
    fun setup() {
        whenever(context.applicationContext).thenReturn(context)
        whenever(rh.gs(any<TextRef>())).thenReturn("not sent")
        plugin = DexcomOnePlusPlugin(
            rh, aapsLogger, preferences, config, notificationManager, DexcomOnePlusAndroidHost(context), persistenceLayer,
            activePlugin, rxBus,
        )
    }

    @AfterEach
    fun tearDownDrivers() {
        OnePlusCgmDrivers.shutdownAll()
    }

    @Test
    fun `the switch defaults to on and is a normal setting`() {
        assertThat(DexcomOnePlusBooleanKey.SendCalibrationToSensor.defaultValue).isTrue()
        assertThat(DexcomOnePlusBooleanKey.SendCalibrationToSensor.engineeringModeOnly).isFalse()
        assertThat(DexcomOnePlusBooleanKey.SendCalibrationToSensor.exportable).isFalse()
    }

    @Test
    fun `switch off returns NotSupported and queues nothing`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(false)
        val driver = OnePlusCgmDrivers.realProduction()
        markSessionUp(driver)
        val before = queueOf(driver).peek()

        val result = plugin.calibrateSensor(glucoseMgdl = 150, bloodAtMs = System.currentTimeMillis())

        assertThat(result).isEqualTo(SensorCalibrationResult.NotSupported)
        assertThat(driver.calibrationPending()).isFalse()
        assertThat(driver.lastCalibrationOutcome()).isNull()
        assertThat(queueOf(driver).peek()).isEqualTo(before)
        assertThat(queueOf(driver).peek()).isNull()
    }

    @Test
    fun `switch on and session down refuses without queueing`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(true)
        val driver = OnePlusCgmDrivers.realProduction()
        giveReading(rate = 0.5)

        val result = plugin.calibrateSensor(glucoseMgdl = 150, bloodAtMs = System.currentTimeMillis())

        assertThat(result).isInstanceOf(SensorCalibrationResult.Refused::class.java)
        assertThat((result as SensorCalibrationResult.Refused).reason).isEqualTo("not sent")
        assertThat(driver.calibrationPending()).isFalse()
        assertThat(driver.lastCalibrationOutcome()).isNull()
        assertThat(queueOf(driver).peek()).isNull()
    }

    @Test
    fun `switch on and a value outside 40 to 400 queues nothing`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(true)
        val driver = OnePlusCgmDrivers.realProduction()
        markSessionUp(driver)
        giveReading(rate = 0.5)

        val low = plugin.calibrateSensor(glucoseMgdl = 39, bloodAtMs = System.currentTimeMillis())
        val high = plugin.calibrateSensor(glucoseMgdl = 401, bloodAtMs = System.currentTimeMillis())

        assertThat(low).isInstanceOf(SensorCalibrationResult.Refused::class.java)
        assertThat(high).isInstanceOf(SensorCalibrationResult.Refused::class.java)
        assertThat(driver.calibrationPending()).isFalse()
        assertThat(driver.lastCalibrationOutcome()).isNull()
        assertThat(queueOf(driver).peek()).isNull()
    }

    @Test
    fun `switch on keeps the first fingerstick when a second one arrives`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(true)
        val driver = OnePlusCgmDrivers.realProduction()
        markSessionUp(driver)
        giveReading(rate = 0.5)
        val now = System.currentTimeMillis()

        val first = plugin.calibrateSensor(glucoseMgdl = 150, bloodAtMs = now)
        val second = plugin.calibrateSensor(glucoseMgdl = 160, bloodAtMs = now)

        assertThat(first).isEqualTo(SensorCalibrationResult.Queued)
        assertThat(second).isInstanceOf(SensorCalibrationResult.Refused::class.java)
        assertThat(driver.calibrationPending()).isTrue()
        assertThat(driver.lastCalibrationOutcome()).isEqualTo(OnePlusCalibrationOutcome.Pending)
        assertThat(queueOf(driver).peek()?.glucoseMgdl).isEqualTo(150)
    }

    @Test
    fun `switch on without a recent reading refuses before the value is shown to the sensor`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(true)
        val driver = OnePlusCgmDrivers.realProduction()
        markSessionUp(driver)
        val now = System.currentTimeMillis()

        assertThat(plugin.sensorCalibrationRefusal(glucoseMgdl = 150, bloodAtMs = now)).isNotNull()
        assertThat(plugin.calibrateSensor(glucoseMgdl = 150, bloodAtMs = now)).isInstanceOf(SensorCalibrationResult.Refused::class.java)
        assertThat(queueOf(driver).peek()).isNull()
    }

    @Test
    fun `switch on and glucose changing fast refuses`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(true)
        val driver = OnePlusCgmDrivers.realProduction()
        markSessionUp(driver)
        giveReading(rate = 3.0)
        val now = System.currentTimeMillis()

        assertThat(plugin.calibrateSensor(glucoseMgdl = 150, bloodAtMs = now)).isInstanceOf(SensorCalibrationResult.Refused::class.java)
        assertThat(queueOf(driver).peek()).isNull()
    }

    @Test
    fun `switch off asks nothing of the sensor`() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)).thenReturn(false)

        assertThat(plugin.sensorCalibrationRefusal(glucoseMgdl = 150, bloodAtMs = System.currentTimeMillis())).isNull()
    }

    /** A production reading one minute old, as `onGlucose` would have left it. */
    private fun giveReading(rate: Double?) {
        val field = DexcomOnePlusPlugin::class.java.getDeclaredField("lastProductionReading")
        field.isAccessible = true
        field.set(plugin, OnePlusGlucoseSample(mgdl = 120.0, timestampMs = System.currentTimeMillis() - 60_000L, trendSlopeMgdlPerMin = rate))
    }

    private fun markSessionUp(driver: OnePlusCgmDriverReal) {
        val field = OnePlusCgmDriverReal::class.java.getDeclaredField("session")
        field.isAccessible = true
        field.set(driver, UpSession)
    }

    private fun queueOf(driver: OnePlusCgmDriverReal): OnePlusCalibrationQueue {
        val field = OnePlusCgmDriverReal::class.java.getDeclaredField("calibrationQueue")
        field.isAccessible = true
        return field.get(driver) as OnePlusCalibrationQueue
    }

    private object UpSession : OnePlusBleSession {
        override fun startWithPairingCode(deviceAddress: String, pairingCode: String) = Unit
        override fun stop(reason: String?) = Unit
        override fun isUp(): Boolean = true
        override fun warmupState() = OnePlusWarmupState(phase = OnePlusWarmupState.Phase.READY)
    }
}
