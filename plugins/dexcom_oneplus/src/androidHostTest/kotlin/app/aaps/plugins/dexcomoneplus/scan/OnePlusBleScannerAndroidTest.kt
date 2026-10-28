package app.aaps.plugins.dexcomoneplus.scan

import android.bluetooth.le.ScanCallback
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusBleScannerAndroidTest {

    @Test
    fun `nameMatches accepts DX02 DX01 DXCM family only`() {
        assertThat(OnePlusBleScannerAndroid.nameMatches("DXCM12")).isTrue()
        assertThat(OnePlusBleScannerAndroid.nameMatches("DX02aS")).isTrue()
        assertThat(OnePlusBleScannerAndroid.nameMatches("DX01xx")).isTrue()
        assertThat(OnePlusBleScannerAndroid.nameMatches("DexcomONE")).isFalse()
        assertThat(OnePlusBleScannerAndroid.nameMatches("Dexcom65")).isFalse()
    }

    @Test
    fun `one silent scan window is not enough to back off`() {
        val state = OnePlusBleScannerAndroid.nextSilentScanState(previousSilentScans = 0, heardAnything = false)

        assertThat(state.silentScans).isEqualTo(1)
        assertThat(state.backOff).isFalse()
    }

    @Test
    fun `two silent windows in a row trigger the cool-down and restart the counter`() {
        val first = OnePlusBleScannerAndroid.nextSilentScanState(previousSilentScans = 0, heardAnything = false)
        val second = OnePlusBleScannerAndroid.nextSilentScanState(first.silentScans, heardAnything = false)

        assertThat(second.backOff).isTrue()
        assertThat(second.silentScans).isEqualTo(0)
    }

    @Test
    fun `a still blind scanner backs off again after the next pair of silent windows`() {
        // The back-off must be repeatable, not a one-shot: a sensor out of range for hours keeps the
        // app scanning less until it answers again.
        var silent = 0
        val backOffs = (1..4).count {
            val state = OnePlusBleScannerAndroid.nextSilentScanState(silent, heardAnything = false)
            silent = state.silentScans
            state.backOff
        }

        assertThat(backOffs).isEqualTo(2)
    }

    @Test
    fun `hearing any advertisement clears the silent counter`() {
        val state = OnePlusBleScannerAndroid.nextSilentScanState(previousSilentScans = 1, heardAnything = true)

        assertThat(state.silentScans).isEqualTo(0)
        assertThat(state.backOff).isFalse()
    }

    @Test
    fun `filtered silence does not itself request a probe until five windows`() {
        assertThat(OnePlusBleScannerAndroid.shouldRunUnfilteredThrottleProbe(0)).isFalse()
        assertThat(OnePlusBleScannerAndroid.shouldRunUnfilteredThrottleProbe(2)).isFalse()
        assertThat(OnePlusBleScannerAndroid.shouldRunUnfilteredThrottleProbe(4)).isFalse()
        assertThat(OnePlusBleScannerAndroid.shouldRunUnfilteredThrottleProbe(5)).isTrue()
        assertThat(OnePlusBleScannerAndroid.shouldRunUnfilteredThrottleProbe(10)).isTrue()
    }

    @Test
    fun `scan-failed back-off is the OS refusal codes only`() {
        assertThat(
            OnePlusBleScannerAndroid.shouldBackOffOnScanFailed(
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
            ),
        ).isTrue()
        assertThat(
            OnePlusBleScannerAndroid.shouldBackOffOnScanFailed(
                ScanCallback.SCAN_FAILED_ALREADY_STARTED,
            ),
        ).isFalse()
    }

    @Test
    fun `nameMatches rejects unrelated names`() {
        assertThat(OnePlusBleScannerAndroid.nameMatches(null)).isFalse()
        assertThat(OnePlusBleScannerAndroid.nameMatches("")).isFalse()
        assertThat(OnePlusBleScannerAndroid.nameMatches("Eversense365")).isFalse()
        assertThat(OnePlusBleScannerAndroid.nameMatches("Galaxy Watch")).isFalse()
    }

    @Test
    fun `target address is normalized for hardware scan filter`() {
        assertThat(
            OnePlusBleScannerAndroid.normalizeTargetAddress("da:3b:12:0d:5b:b7"),
        ).isEqualTo("DA:3B:12:0D:5B:B7")
    }

    @Test
    fun `FEBC neighbours count as G7 family unless they carry a G6 marketing name`() {
        // G7 / ONE+ frequently advertise without a local name.
        assertThat(OnePlusBleScannerAndroid.isG7FamilyAdvertisement(null)).isTrue()
        assertThat(OnePlusBleScannerAndroid.isG7FamilyAdvertisement("DX021H")).isTrue()
        assertThat(OnePlusBleScannerAndroid.isG7FamilyAdvertisement("DXCM99")).isTrue()
        assertThat(OnePlusBleScannerAndroid.isG7FamilyAdvertisement("Dexcom65")).isFalse()
        assertThat(OnePlusBleScannerAndroid.isG7FamilyAdvertisement("Galaxy Watch")).isFalse()
    }

    @Test
    fun `wait result exposes the strongest foreign transmitter for the stale-MAC diagnostic`() {
        val weak = OnePlusScanResult(address = "AA:BB:CC:DD:EE:01", name = "DX021H", rssi = -88)
        val strong = OnePlusScanResult(address = "AA:BB:CC:DD:B8:92", name = "DX021H", rssi = -51)

        val result = OnePlusAdvWaitResult(target = null, foreign = listOf(weak, strong))

        assertThat(result.strongestForeign()).isEqualTo(strong)
        assertThat(OnePlusAdvWaitResult().strongestForeign()).isNull()
    }

    // ------------ autoSelectSingle(): what the screen may decide on its own ------------

    @Test
    fun `nothing heard is nothing to connect to`() {
        assertThat(OnePlusBleScannerAndroid.autoSelectSingle(emptyList())).isNull()
    }

    @Test
    fun `one sensor in range may be selected for the user`() {
        val only = OnePlusScanResult(address = "AA:BB:CC:DD:EE:01", name = "DX021H", rssi = -60)

        assertThat(OnePlusBleScannerAndroid.autoSelectSingle(listOf(only))).isEqualTo(only)
    }

    @Test
    fun `two sensors in range are a question for the user, not a score`() {
        // The 4-digit code is not in the advertisement, so nothing here can tell them apart.
        val near = OnePlusScanResult(address = "AA:BB:CC:DD:EE:01", name = "DX021H", rssi = -45)
        val far = OnePlusScanResult(address = "AA:BB:CC:DD:EE:02", name = "DX02aS", rssi = -85)

        assertThat(OnePlusBleScannerAndroid.autoSelectSingle(listOf(near, far))).isNull()
    }

    @Test
    fun `the sensor being replaced is not picked again when another one answers`() {
        // Old sensor still on the arm and loud, new sensor just applied and quiet. Selecting the
        // loud one would connect the user back to the sensor they are replacing.
        val storedSensor = OnePlusScanResult(address = "AA:BB:CC:DD:EE:FF", name = "DX02aS", rssi = -45)
        val newSensor = OnePlusScanResult(address = "11:22:33:44:55:66", name = "DX021H", rssi = -80)

        assertThat(OnePlusBleScannerAndroid.autoSelectSingle(listOf(storedSensor, newSensor))).isNull()
    }

    // ------------ autoSelect(): the code on screen decides whether anything is sticky ------------

    @Test
    fun `a new code selects nothing while two sensors answer`() {
        // scanHintFor drops the stored MAC when the code is another sensor's, so nothing is sticky.
        val hintForNewSensor = OnePlusStoredSession(identity = OnePlusSensorIdentity(pin = "5678"))
        val first = OnePlusScanResult(address = STORED_MAC, name = "DX02aS", rssi = -45)
        val second = OnePlusScanResult(address = "11:22:33:44:55:66", name = "DX021H", rssi = -80)

        assertThat(OnePlusBleScannerAndroid.autoSelect(listOf(first, second), hintForNewSensor)).isNull()
    }

    @Test
    fun `a new code still takes the single sensor that answers`() {
        val hintForNewSensor = OnePlusStoredSession(identity = OnePlusSensorIdentity(pin = "5678"))
        val only = OnePlusScanResult(address = "11:22:33:44:55:66", name = "DX021H", rssi = -70)

        assertThat(OnePlusBleScannerAndroid.autoSelect(listOf(only), hintForNewSensor)).isEqualTo(only)
    }

    @Test
    fun `the stored sensor stays selected while its own code is on screen`() {
        // A reconnect: the user is not choosing a sensor, so it is theirs even among several, and
        // even before the scan has heard it.
        val reconnect = storedSession()
        val neighbour = OnePlusScanResult(address = "11:22:33:44:55:66", name = "DX021H", rssi = -50)

        val beforeAnyScan = OnePlusBleScannerAndroid.autoSelect(emptyList(), reconnect)!!
        assertThat(beforeAnyScan.address).isEqualTo(STORED_MAC)
        assertThat(beforeAnyScan.name).isEqualTo("DX02aS")

        val amongOthers = OnePlusBleScannerAndroid.autoSelect(listOf(neighbour), reconnect)!!
        assertThat(amongOthers.address).isEqualTo(STORED_MAC)
    }

    @Test
    fun `a live sighting of the stored sensor wins over the stored one`() {
        // The live hit carries the fresh ADV the driver connects in-window with.
        val live = OnePlusScanResult(address = STORED_MAC, name = "DX02aS", rssi = -62)
        val neighbour = OnePlusScanResult(address = "11:22:33:44:55:66", name = "DX021H", rssi = -50)

        val picked = OnePlusBleScannerAndroid.autoSelect(listOf(neighbour, live), storedSession())

        assertThat(picked).isSameInstanceAs(live)
    }

    private fun storedSession() = OnePlusStoredSession(
        identity = OnePlusSensorIdentity(pin = "1234"),
        lastMac = STORED_MAC,
        lastDeviceName = "DX02aS",
    )

    private companion object {

        const val STORED_MAC = "AA:BB:CC:DD:EE:FF"
    }
}
