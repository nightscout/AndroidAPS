package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefs
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerStub
import app.aaps.plugins.dexcomoneplus.session.OnePlusBleSession
import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationQueue
import app.aaps.plugins.dexcomoneplus.session.OnePlusCpuLease
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Who may hand a fingerstick to a sensor.
 *
 * The refusals checked here cannot be undone if they are wrong: a sensor keeps a calibration for
 * good. Each refusal asserts the queue is still empty, or still holding the value that was already
 * waiting — nothing new was accepted.
 *
 * The session-up cases are the range and occupied-queue refusals, which the driver only reaches
 * once a session is up.
 */
class OnePlusCgmDriverCalibrationTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `a pre-soak sensor is never calibrated`() {
        val staging = OnePlusCgmDriverReal(storeNamespace = OnePlusCgmDrivers.STAGING_NAMESPACE)
        markSessionUp(staging)

        assertThat(staging.offerCalibration(glucoseMgdl = 209, bloodAtMs = now)).isFalse()
        assertThat(staging.calibrationPending()).isFalse()
        assertThat(staging.lastCalibrationOutcome()).isNull()
        assertThat(queueOf(staging).peek()).isNull()
    }

    @Test
    fun `a promoted instance reads the live namespace`() {
        val staging = OnePlusCgmDriverReal(storeNamespace = OnePlusCgmDrivers.STAGING_NAMESPACE)
        markSessionUp(staging)
        assertThat(staging.offerCalibration(glucoseMgdl = 209, bloodAtMs = now)).isFalse()
        assertThat(queueOf(staging).peek()).isNull()

        staging.rebindStore(null)

        assertThat(staging.offerCalibration(glucoseMgdl = 209, bloodAtMs = now)).isTrue()
        assertThat(staging.calibrationPending()).isTrue()
        assertThat(queueOf(staging).peek()?.glucoseMgdl).isEqualTo(209)
    }

    @Test
    fun `nothing is queued without a session`() {
        // No sensor is being followed at all, so nothing would ever take the value.
        val production = OnePlusCgmDriverReal()

        assertThat(production.offerCalibration(glucoseMgdl = 209, bloodAtMs = now)).isFalse()
        assertThat(production.calibrationPending()).isFalse()
        assertThat(production.lastCalibrationOutcome()).isNull()
        assertThat(queueOf(production).peek()).isNull()
    }

    @Test
    fun `a session between two radio windows still takes the value`() {
        // A G7 / ONE+ is connected a few seconds every 5 minutes. Between two windows the session is
        // not "up", and refusing there refused almost every fingerstick (field report 2026-10-11).
        val production = OnePlusCgmDriverReal()
        setSession(production, DownSession)

        assertThat(production.offerCalibration(glucoseMgdl = 94, bloodAtMs = now)).isTrue()
        assertThat(production.calibrationPending()).isTrue()
    }

    @Test
    fun `a promoted pre-soak instance takes the value`() {
        // A promotion keeps the instance and its session, and only rebinds the store to production.
        val promoted = OnePlusCgmDriverReal(storeNamespace = OnePlusCgmDrivers.STAGING_NAMESPACE)
        setSession(promoted, DownSession)
        promoted.rebindStore(null)

        assertThat(promoted.offerCalibration(glucoseMgdl = 94, bloodAtMs = now)).isTrue()
        assertThat(promoted.calibrationPending()).isTrue()
    }

    @Test
    fun `a value outside 40 to 400 is not queued`() {
        val production = OnePlusCgmDriverReal()
        markSessionUp(production)

        assertThat(production.offerCalibration(glucoseMgdl = 39, bloodAtMs = now)).isFalse()
        assertThat(production.offerCalibration(glucoseMgdl = 401, bloodAtMs = now)).isFalse()
        assertThat(production.calibrationPending()).isFalse()
        assertThat(production.lastCalibrationOutcome()).isNull()
        assertThat(queueOf(production).peek()).isNull()
    }

    @Test
    fun `a second fingerstick does not replace the one already waiting`() {
        val production = OnePlusCgmDriverReal()
        markSessionUp(production)

        assertThat(production.offerCalibration(glucoseMgdl = 150, bloodAtMs = now)).isTrue()
        assertThat(production.offerCalibration(glucoseMgdl = 160, bloodAtMs = now)).isFalse()
        assertThat(production.calibrationPending()).isTrue()
        assertThat(production.lastCalibrationOutcome()).isEqualTo(OnePlusCalibrationOutcome.Pending)
        assertThat(queueOf(production).peek()?.glucoseMgdl).isEqualTo(150)
    }

    @Test
    fun `the stub driver takes nothing and says so`() {
        val stub = OnePlusCgmDriverStub()

        assertThat(stub.offerCalibration(glucoseMgdl = 209, bloodAtMs = now)).isFalse()
        assertThat(stub.lastCalibrationOutcome()).isNull()
        assertThat(stub.calibrationPending()).isFalse()
    }

    // A value confirmed for one sensor or session must never reach the next one. Each event below
    // empties the queue, so the next session has nothing to write, and says "not sent".

    @Test
    fun `a disconnect drops the waiting value as not sent`() {
        val production = productionWithWaitingValue()

        production.disconnect()

        assertDroppedForSensorChange(production)
    }

    @Test
    fun `a shutdown drops the waiting value as not sent`() {
        val production = productionWithWaitingValue()

        production.shutdown()

        assertDroppedForSensorChange(production)
    }

    @Test
    fun `a promotion drops a value waiting on the instance`() {
        val production = productionWithWaitingValue()

        production.rebindStore(null)

        assertDroppedForSensorChange(production)
    }

    @Test
    fun `a new sensor identity drops the waiting value`() {
        val production = productionWithWaitingValue()

        production.saveIdentity(OnePlusSensorIdentity(pin = "5678"))

        assertDroppedForSensorChange(production)
    }

    @Test
    fun `a new pairing drops the waiting value before the new session starts`() {
        val production = productionWithWaitingValue()
        production.attach(NoRadioPlatform())

        production.connect(deviceAddress = "AA:BB:CC:DD:EE:01", pairingCode = "5678")

        assertDroppedForSensorChange(production)
        production.shutdown()
    }

    @Test
    fun `a session restart drops the value waiting for the session that ended`() {
        val production = OnePlusCgmDriverReal()
        production.attach(NoRadioPlatform())
        production.saveIdentity(OnePlusSensorIdentity(pin = "1234"))
        production.sensorStore()?.saveLastMac("AA:BB:CC:DD:EE:02")
        production.sensorStore()?.saveSharedKey(ByteArray(16) { 1 })
        setSession(production, DownSession)
        assertThat(production.offerCalibration(glucoseMgdl = 150, bloodAtMs = now)).isTrue()
        // The session ends on its own (released), and the watchdog resumes a new one.
        setSession(production, null)

        assertThat(production.resumeStoredSession()).isTrue()

        assertDroppedForSensorChange(production)
        production.shutdown()
    }

    private fun productionWithWaitingValue(): OnePlusCgmDriverReal {
        val production = OnePlusCgmDriverReal()
        setSession(production, DownSession)
        assertThat(production.offerCalibration(glucoseMgdl = 150, bloodAtMs = now)).isTrue()
        assertThat(production.calibrationPending()).isTrue()
        return production
    }

    private fun assertDroppedForSensorChange(driver: OnePlusCgmDriverReal) {
        assertThat(driver.calibrationPending()).isFalse()
        assertThat(queueOf(driver).peek()).isNull()
        assertThat(driver.lastCalibrationOutcome())
            .isEqualTo(OnePlusCalibrationOutcome.NotSent(OnePlusCalibrationOutcome.NotSent.Reason.SENSOR_CHANGED))
    }

    private fun markSessionUp(driver: OnePlusCgmDriverReal) = setSession(driver, UpSession)

    private fun setSession(driver: OnePlusCgmDriverReal, session: OnePlusBleSession?) {
        val field = OnePlusCgmDriverReal::class.java.getDeclaredField("session")
        field.isAccessible = true
        field.set(driver, session)
    }

    private fun queueOf(driver: OnePlusCgmDriverReal): OnePlusCalibrationQueue {
        val field = OnePlusCgmDriverReal::class.java.getDeclaredField("calibrationQueue")
        field.isAccessible = true
        return field.get(driver) as OnePlusCalibrationQueue
    }

    private object UpSession : FakeSession(up = true)

    private object DownSession : FakeSession(up = false)

    private open class FakeSession(private val up: Boolean) : OnePlusBleSession {
        override fun startWithPairingCode(deviceAddress: String, pairingCode: String) = Unit
        override fun stop(reason: String?) = Unit
        override fun isUp(): Boolean = up
        override fun warmupState(): OnePlusWarmupState =
            OnePlusWarmupState(phase = OnePlusWarmupState.Phase.READY)
    }

    /**
     * A host with stores and a scanner but no radio. A session that a test starts fails at once on
     * the BLE thread when it asks for a GATT client, so nothing is ever written to a sensor.
     */
    private class NoRadioPlatform : OnePlusPlatform {

        private val files = mutableMapOf<String, MemoryPrefs>()

        override fun sensorPrefs(fileName: String): OnePlusSensorPrefs = files.getOrPut(fileName) { MemoryPrefs() }
        override fun scanner(hint: OnePlusStoredSession?, slot: String): OnePlusBleScanner = OnePlusBleScannerStub()
        override fun gattClient(profile: OemDeviceProfile): OnePlusGattClient =
            throw IllegalStateException("no radio in this test")

        override fun cpuLease(slot: String): OnePlusCpuLease = object : OnePlusCpuLease {
            override fun hold(maxMillis: Long): OnePlusCpuLease.Hold? = null
        }
    }

    private class MemoryPrefs : OnePlusSensorPrefs {

        private val values = mutableMapOf<String, Any>()

        override fun getString(key: String): String? = values[key] as? String
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(block: OnePlusSensorPrefs.Editor.() -> Unit) {
            object : OnePlusSensorPrefs.Editor {
                override fun putString(key: String, value: String?) {
                    if (value == null) values.remove(key) else values[key] = value
                }

                override fun putLong(key: String, value: Long) {
                    values[key] = value
                }

                override fun putInt(key: String, value: Int) {
                    values[key] = value
                }

                override fun putBoolean(key: String, value: Boolean) {
                    values[key] = value
                }

                override fun remove(key: String) {
                    values.remove(key)
                }

                override fun clear() {
                    values.clear()
                }
            }.block()
        }
    }
}
