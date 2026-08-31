package app.aaps.implementation.receivers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The network state this store derives and publishes.
 *
 * The path monitor itself is not exercised here - it needs a real network stack and calls back on a
 * dispatch queue. What is tested is everything the rest of AAPS actually reads: the flow, the three
 * derived booleans, and the fact that "nothing known yet" is distinct from "known to be offline".
 *
 * That last distinction matters: `ReceiverDelegate` decides whether Nightscout may sync from these
 * values, and treating an unknown state as offline would stop syncing on a perfectly good
 * connection.
 */
class IosReceiverStatusStoreTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private fun store() = IosReceiverStatusStore(SilentLogger)

    /** Nothing has been observed yet - which is not the same as being offline. */
    @Test
    fun `it starts with nothing known`() {
        val s = store()

        assertNull(s.networkStatusFlow.value)
        assertFalse(s.isKnownNetworkStatus)
        assertFalse(s.isConnected)
    }

    @Test
    fun `a wifi connection is connected and is wifi`() {
        val s = store()

        s.setNetworkStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true))

        assertTrue(s.isKnownNetworkStatus)
        assertTrue(s.isConnected)
        assertTrue(s.isWifiConnected)
    }

    @Test
    fun `a cellular connection is connected but not wifi`() {
        val s = store()

        s.setNetworkStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true))

        assertTrue(s.isConnected)
        assertFalse(s.isWifiConnected)
    }

    /** An empty status is what the monitor reports for a path that is not satisfied. */
    @Test
    fun `no interfaces means known but not connected`() {
        val s = store()

        s.setNetworkStatus(ReceiverStatusStore.NetworkStatus())

        assertTrue(s.isKnownNetworkStatus, "an observed offline state is still knowledge")
        assertFalse(s.isConnected)
    }

    @Test
    fun `the latest status replaces the previous one`() {
        val s = store()

        s.setNetworkStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true))
        s.setNetworkStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true))

        assertFalse(s.isWifiConnected)
        assertTrue(s.isConnected)
    }

    /**
     * Pins the two fields iOS cannot answer.
     *
     * Both are false because no public API reports them. `roaming` in particular has a cost:
     * `ReceiverDelegate` permits cellular sync when not roaming, so a user abroad who turned off
     * "sync while roaming" still syncs and may be charged. Recorded in `_docs/ios_blockers.md`; this
     * test is here so the limitation is not mistaken for an oversight.
     */
    @Test
    fun `roaming and vpn are reported false because iOS cannot say`() {
        val s = store()

        s.setNetworkStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true))
        val status = s.networkStatusFlow.value

        assertFalse(status!!.roaming)
        assertFalse(status.vpnConnected)
        assertEquals("", status.ssid, "SSID needs an entitlement that is not arranged yet")
    }

    @Test
    fun `charging status can be published and read back`() {
        val s = store()

        s.setChargingStatus(ReceiverStatusStore.ChargingStatus(isCharging = true, batteryLevel = 55))

        assertEquals(55, s.chargingStatusFlow.value?.batteryLevel)
        assertTrue(s.chargingStatusFlow.value?.isCharging == true)
    }
}
