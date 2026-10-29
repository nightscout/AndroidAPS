package app.aaps.plugins.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CgmSensorAlertProvider
import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.parse.Libre3PatchState
import app.aaps.plugins.libre3.parse.Libre3PatchStatus
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The Libre 3 plugin publishes its sensor state for the central sensor alarms, from the driver
 * callbacks it already gets, and says nothing while it is stopped.
 *
 * Robolectric, because a warm-up state also posts the status bar message.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [35])
class Libre3PluginSensorAlertTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newPlugin() = Libre3NativePlugin(
        mock<ResourceHelper>(), AAPSLoggerTest(), mock<Preferences>(), mock<Config>(), mock<NotificationManager>(),
        context, mock<PersistenceLayer>(), mock<ActivePlugin>(),
    )

    private val plugin = newPlugin()

    private val wearMinutes = 14 * 24 * 60

    /** A sensor whose life and 12 h grace are over: it ended an hour ago. */
    private fun storeEndedSensor(): Long {
        val expiresAtMs = System.currentTimeMillis() - 60L * 60L * 1000L
        val activatedAtMs = expiresAtMs - wearMinutes * 60_000L - Libre3Staging.SENSOR_GRACE_MS
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(identity(activatedAtMs))).isTrue()
        return expiresAtMs
    }

    private fun identity(activatedAtMs: Long) = Libre3SensorIdentity(
        serialNumber = "MH0ALERT02",
        bleAddress = "AA:BB:CC:DD:EE:43",
        blePin = byteArrayOf(1, 2, 3, 4),
        receiverId = 1234,
        generation = 0,
        warmupMinutes = 60,
        wearDurationMinutes = wearMinutes,
        activatedAtMs = activatedAtMs,
    )

    private fun storeRunningSensor() {
        val identity = Libre3SensorIdentity(
            serialNumber = "MH0ALERT01",
            bleAddress = "AA:BB:CC:DD:EE:42",
            blePin = byteArrayOf(1, 2, 3, 4),
            receiverId = 1234,
            generation = 0,
            warmupMinutes = 60,
            wearDurationMinutes = 14 * 24 * 60,
            // Started a day ago, so the end of life is far away.
            activatedAtMs = System.currentTimeMillis() - 24L * 60L * 60L * 1000L,
        )
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(identity)).isTrue()
    }

    @After
    fun tearDown() {
        Libre3SensorStore(context, null).clearAll()
        runCatching { Libre3CgmDrivers.default().shutdown() }
    }

    @Test
    fun `the plugin reports its sensor state`() {
        assertThat(plugin).isInstanceOf(CgmSensorAlertProvider::class.java)
        storeRunningSensor()
        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Ok)

        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.FAILED))

        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.OTHER))
    }

    @Test
    fun `a lost link is reported, and cleared when the link is back`() {
        storeRunningSensor()

        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.RECONNECTING))
        assertThat(plugin.sensorAlertState.value).isInstanceOf(CgmSensorAlertState.SignalLost::class.java)

        // A retry passes through CONNECTING: the link is not back yet.
        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.CONNECTING))
        assertThat(plugin.sensorAlertState.value).isInstanceOf(CgmSensorAlertState.SignalLost::class.java)

        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.READY))
        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `a fault in the health message of the sensor is reported, and a good one clears it`() {
        storeRunningSensor()

        plugin.productionWatcher.onPatchStatus(patchStatus(errorData = 3))
        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))

        plugin.productionWatcher.onPatchStatus(patchStatus(errorData = 0))
        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Ok)
    }

    /** The urgent alarm sounds once per end: neither the first end message nor a restart may move the episode. */
    @Test
    fun `an ended sensor keeps one expired episode, through the end message and a restart`() {
        val expiresAtMs = storeEndedSensor()
        val expired = CgmSensorAlertState.Expired(expiresAtMs)
        assertThat(plugin.sensorAlertState.value).isEqualTo(expired)

        plugin.productionWatcher.onPatchStatus(patchStatus(errorData = 5))
        assertThat(plugin.sensorAlertState.value).isEqualTo(expired)

        val restarted = newPlugin()
        assertThat(restarted.sensorAlertState.value).isEqualTo(expired)
        restarted.productionWatcher.onPatchStatus(patchStatus(errorData = 5))
        assertThat(restarted.sensorAlertState.value).isEqualTo(expired)
    }

    @Test
    fun `an unknown error code fails the sensor only on the second health message in a row`() {
        storeRunningSensor()

        plugin.productionWatcher.onPatchStatus(patchStatus(errorData = 99))
        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Ok)

        plugin.productionWatcher.onPatchStatus(patchStatus(errorData = 99))
        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
    }

    @Test
    fun `a stopped plugin reports ok`() = runTest {
        storeRunningSensor()
        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.FAILED))

        plugin.onStop()

        assertThat(plugin.sensorAlertState.value).isEqualTo(CgmSensorAlertState.Ok)
    }

    private fun patchStatus(errorData: Int) = Libre3PatchStatus(
        lifeCount = 100, errorData = errorData, eventData = 0, index = 0, patchState = Libre3PatchState(4),
        currentLifeCount = 100, stackDisconnectReason = 0, appDisconnectReason = 0,
    )
}
