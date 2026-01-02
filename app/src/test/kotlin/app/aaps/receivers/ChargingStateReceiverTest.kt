package app.aaps.receivers

import android.annotation.SuppressLint
import android.content.Intent
import android.os.BatteryManager
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.implementation.receivers.ReceiverStatusStoreImpl
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChargingStateReceiverTest : TestBaseWithProfile() {

    private lateinit var chargingStateReceiver: ChargingStateReceiver

    lateinit var receiverStatusStore: ReceiverStatusStore

    @BeforeEach
    fun setUp() {
        receiverStatusStore = ReceiverStatusStoreImpl(context, rxBus)
        chargingStateReceiver = ChargingStateReceiver().also {
            it.aapsLogger = aapsLogger
            it.rxBus = rxBus
            it.receiverStatusStore = receiverStatusStore
        }
    }

    // Helper to simulate the result of context.registerReceiver()
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun simulateBatteryState(level: Int, scale: Int, pluggedType: Int) {
        val intent = mock<Intent>()
        whenever(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).thenReturn(level)
        whenever(intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)).thenReturn(scale)
        whenever(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)).thenReturn(pluggedType)

        // Mock the specific call the receiver makes
        whenever(context.registerReceiver(anyOrNull(), any())).thenReturn(intent)
    }

    @Test
    fun `grabChargingState sends correct event when charging via USB`() {
        // Arrange
        simulateBatteryState(level = 75, scale = 100, pluggedType = BatteryManager.BATTERY_PLUGGED_USB)

        // Act
        val result = chargingStateReceiver.grabChargingState(context)

        // Assert
        assertThat(result.isCharging).isTrue()
        assertThat(result.batteryLevel).isEqualTo(75)
        assertThat(receiverStatusStore.lastChargingEvent).isEqualTo(result)
    }

    @Test
    fun `grabChargingState sends correct event when charging via AC`() {
        // Arrange
        simulateBatteryState(level = 30, scale = 100, pluggedType = BatteryManager.BATTERY_PLUGGED_AC)

        // Act
        val result = chargingStateReceiver.grabChargingState(context)

        // Assert
        assertThat(result.isCharging).isTrue()
        assertThat(result.batteryLevel).isEqualTo(30)
        assertThat(receiverStatusStore.lastChargingEvent).isEqualTo(result)
    }

    @Test
    fun `grabChargingState sends correct event when charging via Wireless`() {
        // Arrange
        simulateBatteryState(level = 90, scale = 100, pluggedType = BatteryManager.BATTERY_PLUGGED_WIRELESS)

        // Act
        val result = chargingStateReceiver.grabChargingState(context)

        // Assert
        assertThat(result.isCharging).isTrue()
        assertThat(result.batteryLevel).isEqualTo(90)
        assertThat(receiverStatusStore.lastChargingEvent).isEqualTo(result)
    }

    @Test
    fun `grabChargingState sends correct event when not charging (unplugged)`() {
        // Arrange
        simulateBatteryState(level = 50, scale = 100, pluggedType = 0)

        // Act
        val result = chargingStateReceiver.grabChargingState(context)

        // Assert
        assertThat(result.isCharging).isFalse()
        assertThat(result.batteryLevel).isEqualTo(50)
        assertThat(receiverStatusStore.lastChargingEvent).isEqualTo(result)
    }

    @Test
    fun `grabChargingState calculates battery level correctly with different scale`() {
        // Arrange
        // 15 / 150 = 10%
        simulateBatteryState(level = 15, scale = 150, pluggedType = 0)

        // Act
        val result = chargingStateReceiver.grabChargingState(context)

        // Assert
        assertThat(result.batteryLevel).isEqualTo(10)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Test
    fun `grabChargingState handles missing battery state and defaults to 0 percent and not charging`() {
        // Arrange
        // Mock registerReceiver to return null, simulating no sticky intent available
        whenever(context.registerReceiver(any(), any())).thenReturn(null)

        // Act
        val result = chargingStateReceiver.grabChargingState(context)

        // Assert
        assertThat(result.isCharging).isFalse()
        assertThat(result.batteryLevel).isEqualTo(0)
        assertThat(receiverStatusStore.lastChargingEvent).isEqualTo(result)
    }
}
