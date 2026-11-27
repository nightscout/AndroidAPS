package app.aaps.plugins.dexcomoneplus.reconnect

import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OemAwareReconnectPolicyTest {

    private val policy = OemAwareReconnectPolicy()

    @Test
    fun `pixel profile retries within connectRetryCount`() {
        val profile = DeviceProfileRegistry.PixelDefault
        assertThat(policy.shouldRetry(0, profile)).isTrue()
        assertThat(policy.shouldRetry(profile.connectRetryCount - 1, profile)).isTrue()
        assertThat(policy.shouldRetry(profile.connectRetryCount, profile)).isFalse()
        // Non-aggressive: flat base delay, independent of attempt index.
        assertThat(policy.nextDelayMs(0, profile)).isEqualTo(profile.connectRetryDelayMs)
        assertThat(policy.nextDelayMs(5, profile)).isEqualTo(profile.connectRetryDelayMs)
    }

    @Test
    fun `aggressive profile uses fast first retry then capped linear backoff`() {
        val profile = DeviceProfileRegistry.SamsungDefault
        assertThat(profile.aggressiveReconnect).isTrue()
        assertThat(profile.connectRetryDelayMs).isEqualTo(10_000L)
        assertThat(profile.requestMtuOnConnect).isFalse()
        assertThat(profile.autoConnectFromAttempt).isEqualTo(0)
        assertThat(profile.requireAdvBeforeConnect).isTrue()
        assertThat(profile.preConnectScanMs).isEqualTo(8_000L)
        // First reconnect is fast to catch the next ADV window (was base*2 = 20 s).
        assertThat(policy.nextDelayMs(1, profile)).isEqualTo(OemAwareReconnectPolicy.FIRST_RETRY_MS)
        // Then linear from the base…
        assertThat(policy.nextDelayMs(2, profile)).isEqualTo(profile.connectRetryDelayMs)
        assertThat(policy.nextDelayMs(3, profile)).isEqualTo(profile.connectRetryDelayMs * 2)
        // …capped so late attempts never stretch to minute-scale gaps.
        assertThat(policy.nextDelayMs(10, profile)).isEqualTo(OemAwareReconnectPolicy.MAX_RECONNECT_DELAY_MS)
    }
}
