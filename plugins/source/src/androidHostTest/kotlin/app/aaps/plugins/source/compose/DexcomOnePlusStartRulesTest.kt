package app.aaps.plugins.source.compose

import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sensor start screen's own decisions.
 *
 * Which step the user is on, and which sensor is offered first.
 */
class DexcomOnePlusStartRulesTest {

    private fun hit(address: String, name: String? = null, rssi: Int = -70) =
        OnePlusScanResult(address = address, name = name, rssi = rssi)

    private fun stored(mac: String?, pin: String = "1234", serial: String? = "ABCD") =
        OnePlusStoredSession(
            identity = OnePlusSensorIdentity(pin = pin, serial = serial),
            lastMac = mac,
        )

    // ------------ currentStep(): the guided flow ------------

    @Test
    fun `a missing permission is the first step whatever else is filled in`() {
        assertEquals(
            DexcomOnePlusStartRules.STEP_PREPARE,
            DexcomOnePlusStartRules.currentStep(hasPermissions = false, hasCode = true, hasDevice = true),
        )
    }

    @Test
    fun `with the permission granted and no code the user is on the code step`() {
        assertEquals(
            DexcomOnePlusStartRules.STEP_CODE,
            DexcomOnePlusStartRules.currentStep(hasPermissions = true, hasCode = false, hasDevice = true),
        )
    }

    @Test
    fun `a code without a chosen sensor is the connect step`() {
        assertEquals(
            DexcomOnePlusStartRules.STEP_CONNECT,
            DexcomOnePlusStartRules.currentStep(hasPermissions = true, hasCode = true, hasDevice = false),
        )
    }

    @Test
    fun `everything in place is the warm-up step`() {
        assertEquals(
            DexcomOnePlusStartRules.STEP_WARMUP,
            DexcomOnePlusStartRules.currentStep(hasPermissions = true, hasCode = true, hasDevice = true),
        )
    }

    // ------------ signalBars(): what the row draws ------------

    @Test
    fun `a stored sensor with no live sighting draws no bars`() {
        assertEquals(0, DexcomOnePlusStartRules.signalBars(0))
    }

    @Test
    fun `a strong signal draws four bars and a weak one draws one`() {
        assertEquals(4, DexcomOnePlusStartRules.signalBars(-55))
        assertEquals(4, DexcomOnePlusStartRules.signalBars(-60))
        assertEquals(3, DexcomOnePlusStartRules.signalBars(-61))
        assertEquals(3, DexcomOnePlusStartRules.signalBars(-70))
        assertEquals(2, DexcomOnePlusStartRules.signalBars(-71))
        assertEquals(2, DexcomOnePlusStartRules.signalBars(-80))
        assertEquals(1, DexcomOnePlusStartRules.signalBars(-81))
        assertEquals(1, DexcomOnePlusStartRules.signalBars(-110))
    }

    // ------------ rank(): the order the list is drawn in ------------

    @Test
    fun `the stored sensor is offered before a stronger neighbour`() {
        val neighbour = hit("AA:AA:AA:AA:AA:AA", name = "DXCM00", rssi = -40)
        val mine = hit("BB:BB:BB:BB:BB:BB", name = "DXCM11", rssi = -85)
        val ranked = DexcomOnePlusStartRules.rank(
            listOf(neighbour, mine),
            stored(mac = "BB:BB:BB:BB:BB:BB"),
        )
        assertEquals(mine.address, ranked.first().address)
    }

    @Test
    fun `without a hint the stronger signal comes first`() {
        val far = hit("AA:AA:AA:AA:AA:AA", name = "DXCM00", rssi = -85)
        val near = hit("BB:BB:BB:BB:BB:BB", name = "DXCM11", rssi = -45)
        val ranked = DexcomOnePlusStartRules.rank(listOf(far, near), hint = null)
        assertEquals(near.address, ranked.first().address)
    }

    @Test
    fun `ranking keeps every sensor it was given`() {
        val devices = listOf(hit("AA:AA:AA:AA:AA:AA"), hit("BB:BB:BB:BB:BB:BB"), hit("CC:CC:CC:CC:CC:CC"))
        val ranked = DexcomOnePlusStartRules.rank(devices, stored(mac = "CC:CC:CC:CC:CC:CC"))
        assertEquals(devices.map { it.address }.toSet(), ranked.map { it.address }.toSet())
    }

    // ------------ adoptsAnotherSensor(): when the screen asks before overwriting ------------

    @Test
    fun `an empty slot adopts without a question`() {
        assertFalse(DexcomOnePlusStartRules.adoptsAnotherSensor(storedMac = null, address = "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `the same sensor in different letter case is not another sensor`() {
        assertFalse(
            DexcomOnePlusStartRules.adoptsAnotherSensor(
                storedMac = "aa:bb:cc:dd:ee:ff",
                address = "AA:BB:CC:DD:EE:FF",
            ),
        )
    }

    @Test
    fun `a different MAC in the slot is another sensor`() {
        assertTrue(
            DexcomOnePlusStartRules.adoptsAnotherSensor(
                storedMac = "AA:BB:CC:DD:EE:FF",
                address = "11:22:33:44:55:66",
            ),
        )
    }
}
