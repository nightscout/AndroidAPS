package app.aaps.plugins.dexcomoneplus.gatt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The driver must not change a link that works: it asks for Android's normal parameters only when the
 * sensor's preferred parameters gave the link a peripheral latency, and only a few times per link.
 */
class OnePlusLinkParameterPolicyTest {

    @Test
    fun aNormalLinkGetsNoRequest() {
        // 45 ms / latency 0: every production sensor in the field logs.
        assertFalse(OnePlusLinkParameterPolicy.requestNormalAfterUpdate(peripheralLatency = 0, requestsThisLink = 0))
    }

    @Test
    fun theSensorsLatency40GetsARequest() {
        // The pre-soak sensor's PPCP: 50-75 ms, latency 40, timeout 6.5 s.
        assertTrue(OnePlusLinkParameterPolicy.requestNormalAfterUpdate(peripheralLatency = 40, requestsThisLink = 0))
    }

    @Test
    fun aPhoneThatNeverHonoursTheRequestIsNotAskedForever() {
        val max = OnePlusLinkParameterPolicy.MAX_REQUESTS_PER_LINK
        assertTrue(OnePlusLinkParameterPolicy.requestNormalAfterUpdate(peripheralLatency = 40, requestsThisLink = max - 1))
        assertFalse(OnePlusLinkParameterPolicy.requestNormalAfterUpdate(peripheralLatency = 40, requestsThisLink = max))
    }

    @Test
    fun theLogLineUsesMillisecondsLikeTheSnoopLog() {
        // Android reports 48 x 1.25 ms and 650 x 10 ms.
        assertEquals("interval=60.0ms latency=40 timeout=6500ms status=0", OnePlusLinkParameterPolicy.describe(interval = 48, latency = 40, timeout = 650, status = 0))
        assertEquals("interval=45.0ms latency=0 timeout=5000ms status=0", OnePlusLinkParameterPolicy.describe(interval = 36, latency = 0, timeout = 500, status = 0))
    }
}
