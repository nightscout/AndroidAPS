package app.aaps.plugins.source

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phone side rules of direct-to-watch.
 */
class DexcomOnePlusWatchDirectRulesTest {

    private fun paired(mac: String = "AA:BB:CC:DD:EE:01", pin: String = "4826") = OnePlusStoredSession(
        identity = OnePlusSensorIdentity(pin = pin, serial = "SN1"),
        lastMac = mac,
        lastDeviceName = "DXCMAB",
        sharedKey = ByteArray(16) { 1 },
    )

    @Test
    fun `a paired production sensor is handed to the watch when the switch is on`() {
        val wanted = DexcomOnePlusWatchDirectRules.wanted(true, DexcomOnePlusWatchDirectRules.sensorOf(paired()))
        assertTrue(wanted.active)
        assertEquals("AA:BB:CC:DD:EE:01", wanted.address)
        assertEquals("4826", wanted.pairingCode)
        assertEquals("SN1", wanted.serial)
        assertEquals("DXCMAB", wanted.sensorName)
    }

    @Test
    fun `the switch off sends an inactive message`() {
        val wanted = DexcomOnePlusWatchDirectRules.wanted(false, DexcomOnePlusWatchDirectRules.sensorOf(paired()))
        assertFalse(wanted.active)
        assertNull(wanted.pairingCode)
        assertNull(wanted.address)
    }

    @Test
    fun `nothing is handed over while the phone does not hold the phone slot`() {
        // No key: the phone has not authenticated, so it does not hold the phone slot.
        val unpaired = paired().copy(sharedKey = null)
        val sensor = DexcomOnePlusWatchDirectRules.sensorOf(unpaired)
        assertFalse(DexcomOnePlusWatchDirectRules.isAvailable(sensor))
        assertFalse(DexcomOnePlusWatchDirectRules.wanted(true, sensor).active)
    }

    @Test
    fun `a forgotten sensor revokes the watch`() {
        assertNull(DexcomOnePlusWatchDirectRules.sensorOf(null))
        assertFalse(DexcomOnePlusWatchDirectRules.wanted(true, null).active)
        // A stored identity without an address is no sensor to follow either.
        assertFalse(DexcomOnePlusWatchDirectRules.wanted(true, DexcomOnePlusWatchDirectRules.sensorOf(paired().copy(lastMac = null))).active)
        assertFalse(DexcomOnePlusWatchDirectRules.wanted(true, DexcomOnePlusWatchDirectRules.sensorOf(paired(pin = "12"))).active)
    }

    @Test
    fun `a new production sensor after a promotion is a change`() {
        val before = DexcomOnePlusWatchDirectRules.wanted(true, DexcomOnePlusWatchDirectRules.sensorOf(paired()))
        val after = DexcomOnePlusWatchDirectRules.wanted(true, DexcomOnePlusWatchDirectRules.sensorOf(paired(mac = "AA:BB:CC:DD:EE:02", pin = "5937")))
        assertTrue(DexcomOnePlusWatchDirectRules.changed(before, after))
        assertFalse(DexcomOnePlusWatchDirectRules.changed(before, before.copy(sensorName = "other name")))
        assertTrue(DexcomOnePlusWatchDirectRules.changed(null, before))
        assertTrue(DexcomOnePlusWatchDirectRules.changed(before, EventData.CgmWatchDirect(active = false)))
    }

    @Test
    fun `a watch on the old sensor is out of step and is corrected at most once a minute`() {
        val wanted = DexcomOnePlusWatchDirectRules.wanted(true, DexcomOnePlusWatchDirectRules.sensorOf(paired()))
        val onOld = EventData.CgmWatchStatus(EventData.CgmWatchState.CONNECTED, address = "AA:BB:CC:DD:EE:99")
        val onNew = EventData.CgmWatchStatus(EventData.CgmWatchState.WAITING, address = "AA:BB:CC:DD:EE:01")
        assertFalse(DexcomOnePlusWatchDirectRules.matches(wanted, onOld))
        assertTrue(DexcomOnePlusWatchDirectRules.matches(wanted, onNew))
        assertTrue(DexcomOnePlusWatchDirectRules.shouldCorrect(wanted, onOld, lastCorrectionAtMs = 0L, nowMs = 100_000L))
        assertFalse(DexcomOnePlusWatchDirectRules.shouldCorrect(wanted, onOld, lastCorrectionAtMs = 90_000L, nowMs = 100_000L))
        assertFalse(DexcomOnePlusWatchDirectRules.shouldCorrect(wanted, onNew, lastCorrectionAtMs = 0L, nowMs = 100_000L))
    }

    @Test
    fun `a revoked watch must report off and a watch that cannot run it is left alone`() {
        val off = EventData.CgmWatchDirect(active = false)
        assertTrue(DexcomOnePlusWatchDirectRules.matches(off, EventData.CgmWatchStatus(EventData.CgmWatchState.OFF)))
        assertFalse(DexcomOnePlusWatchDirectRules.matches(off, EventData.CgmWatchStatus(EventData.CgmWatchState.CONNECTED, address = "AA")))
        assertTrue(DexcomOnePlusWatchDirectRules.matches(off, EventData.CgmWatchStatus(EventData.CgmWatchState.NOT_SUPPORTED)))
    }

    @Test
    fun `the sensor never prints its pairing code`() {
        val sensor = DexcomOnePlusWatchDirectRules.sensorOf(paired(pin = "4826"))
        assertFalse(sensor.toString().contains("4826"))
    }
}
