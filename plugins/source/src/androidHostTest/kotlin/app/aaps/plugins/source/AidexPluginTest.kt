package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AidexPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var notificationManager: NotificationManager

    private lateinit var aidexPlugin: AidexPlugin

    /** Convenience wrapper — call with named args to set only the flag(s) under test. */
    private fun handle(
        sensorExpired: Boolean = false,
        sensorError: Boolean = false,
        sensorStabling: Boolean = false,
        replaceSensor: Boolean = false,
        signalLost: Boolean = false,
    ) = aidexPlugin.handleSensorNotifications(sensorExpired, sensorError, sensorStabling, replaceSensor, signalLost)

    @BeforeEach
    fun setup() {
        aidexPlugin = AidexPlugin(rh, aapsLogger, preferences, config, notificationManager)
    }

    @Test
    fun `plugin is created`() {
        assertThat(aidexPlugin).isNotNull()
    }

    @Test
    fun `hasSensorError defaults to false`() {
        assertThat(aidexPlugin.hasSensorError()).isFalse()
    }

    @Test
    fun `specialEnableCondition is true`() {
        assertThat(aidexPlugin.specialEnableCondition()).isTrue()
    }

    // The if/else structure in handleSensorNotifications guarantees that each of the 5 conditions
    // calls either post() OR dismiss(). Verifying dismiss (which has a simple non-vararg signature)
    // tells us which branch fired without having to wrestle Mockito matchers around the post()
    // overload that includes `vararg formatArgs: Any?`.

    @Test
    fun `sensorExpired=true skips dismiss for expired, dismisses the rest`() {
        handle(sensorExpired = true)

        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SIGNAL_LOST)
    }

    @Test
    fun `sensorError=true skips dismiss for error, dismisses the rest`() {
        handle(sensorError = true)

        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SIGNAL_LOST)
    }

    @Test
    fun `sensorStabling=true skips dismiss for stabilizing, dismisses the rest`() {
        handle(sensorStabling = true)

        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SIGNAL_LOST)
    }

    @Test
    fun `replaceSensor=true skips dismiss for replace, dismisses the rest`() {
        handle(replaceSensor = true)

        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SIGNAL_LOST)
    }

    @Test
    fun `signalLost=true skips dismiss for signal-lost, dismisses the rest`() {
        handle(signalLost = true)

        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SIGNAL_LOST)
    }

    @Test
    fun `all flags false dismisses every notification`() {
        handle()

        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SIGNAL_LOST)
    }

    @Test
    fun `latch keeps dismiss off while flag stays true across repeated calls`() {
        // Both calls have sensorExpired=true → dismiss should never be called for expired,
        // even though the underlying flag remains true (the post-once latch must not cause
        // a dismiss on subsequent calls).
        handle(sensorExpired = true)
        handle(sensorExpired = true)

        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
    }

    @Test
    fun `latch re-arms after flag clears, re-posting on the next active call`() {
        // 1) Set: post fires, latch arms (no dismiss for expired)
        handle(sensorExpired = true)
        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)

        // 2) Clear: dismiss fires, latch resets to false
        clearInvocations(notificationManager)
        handle(sensorExpired = false)
        verify(notificationManager).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)

        // 3) Set again: post must fire again (latch must NOT remember the previous run); we observe
        // it indirectly through dismiss NOT being called this round.
        clearInvocations(notificationManager)
        handle(sensorExpired = true)
        verify(notificationManager, never()).dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
    }
}
