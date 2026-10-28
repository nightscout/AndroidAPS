package app.aaps.plugins.dexcomoneplus.identity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusAdvCandidateTest {

    @Test
    fun isG7FamilyName_matchesJugglucoPrefixes() {
        assertThat(OnePlusAdvCandidate.isG7FamilyName("DX02aS")).isTrue()
        assertThat(OnePlusAdvCandidate.isG7FamilyName("DX01xx")).isTrue()
        assertThat(OnePlusAdvCandidate.isG7FamilyName("DXCMab")).isTrue()
        assertThat(OnePlusAdvCandidate.isG7FamilyName("DexcomONE")).isFalse()
        assertThat(OnePlusAdvCandidate.isG7FamilyName("Dexcom65")).isFalse()
    }

    @Test
    fun nameMatchesSoft_rejectsG6MarketingNames() {
        assertThat(OnePlusAdvCandidate.nameMatchesSoft("DX02aS")).isTrue()
        assertThat(OnePlusAdvCandidate.nameMatchesSoft("Dexcom65")).isFalse()
        assertThat(OnePlusAdvCandidate.nameMatchesSoft("DexcomONE")).isFalse()
    }

    @Test
    fun isCandidate_rejectsDexcom65UnlessStickyMac() {
        assertThat(
            OnePlusAdvCandidate.isCandidate("Dexcom65", "F2:08:F3:22:6B:77", session = null),
        ).isFalse()
        val sticky = OnePlusStoredSession(
            identity = OnePlusSensorIdentity(pin = "1234"),
            lastMac = "F2:08:F3:22:6B:77",
            lastDeviceName = "Dexcom65",
        )
        // Sticky MAC still matches (reconnect to stored device) — UI should not offer it for ONE+.
        assertThat(
            OnePlusAdvCandidate.isCandidate("Dexcom65", "F2:08:F3:22:6B:77", sticky),
        ).isTrue()
    }

    @Test
    fun stickyMacAndNamePreferred() {
        val session = OnePlusStoredSession(
            identity = OnePlusSensorIdentity(pin = "1234", serial = "SER1"),
            lastMac = "AA:BB:CC:DD:EE:FF",
            lastDeviceName = "DX02aS",
        )
        assertThat(
            OnePlusAdvCandidate.isCandidate("DX02aS", "11:22:33:44:55:66", session),
        ).isTrue()
        assertThat(
            OnePlusAdvCandidate.isCandidate("Other", "AA:BB:CC:DD:EE:FF", session),
        ).isTrue()
        assertThat(
            OnePlusAdvCandidate.rankScore("DX02aS", "AA:BB:CC:DD:EE:FF", -70, session),
        ).isGreaterThan(
            OnePlusAdvCandidate.rankScore("DX02zz", "11:22:33:44:55:66", -40, session),
        )
    }

    // ------------ scanHintFor(): rank this scan from the code on screen ------------

    @Test
    fun scanHintFor_sameCode_keepsTheStoredSensorSticky() {
        val stored = storedSensor(pin = "1234")
        assertThat(OnePlusAdvCandidate.scanHintFor(stored, OnePlusSensorIdentity(pin = "1234")))
            .isEqualTo(stored)
    }

    @Test
    fun scanHintFor_newCode_dropsStoredMacAndName() {
        val hint = OnePlusAdvCandidate.scanHintFor(storedSensor(pin = "1234"), OnePlusSensorIdentity(pin = "5678"))!!

        assertThat(hint.identity.pin).isEqualTo("5678")
        assertThat(hint.lastMac).isNull()
        assertThat(hint.lastDeviceName).isNull()
    }

    @Test
    fun scanHintFor_serialDecidesWhenBothSidesKnowIt() {
        // The code is not unique, so the same four digits on another serial is another sensor…
        val stored = storedSensor(pin = "1234", serial = "SER1")
        val other = OnePlusAdvCandidate.scanHintFor(stored, OnePlusSensorIdentity(pin = "1234", serial = "SER2"))!!
        assertThat(other.lastMac).isNull()

        // …and the same serial is the same sensor, whatever the code says.
        assertThat(OnePlusAdvCandidate.scanHintFor(stored, OnePlusSensorIdentity(pin = "9999", serial = "SER1")))
            .isEqualTo(stored)
    }

    @Test
    fun scanHintFor_firstPairingHasNothingSticky() {
        assertThat(OnePlusAdvCandidate.scanHintFor(null, null)).isNull()

        val fresh = OnePlusAdvCandidate.scanHintFor(null, OnePlusSensorIdentity(pin = "1234"))!!
        assertThat(fresh.lastMac).isNull()
        assertThat(fresh.lastDeviceName).isNull()
    }

    @Test
    fun scanHintFor_noCodeOnScreen_leavesTheStoreAlone() {
        // Nothing on screen contradicts the store, so a plain reconnect still ranks as before.
        val stored = storedSensor(pin = "1234")
        assertThat(OnePlusAdvCandidate.scanHintFor(stored, onScreen = null)).isEqualTo(stored)
    }

    @Test
    fun newCode_theOldTransmitterLosesItsBoost() {
        val stored = storedSensor(pin = "1234")
        val hint = OnePlusAdvCandidate.scanHintFor(stored, OnePlusSensorIdentity(pin = "5678"))

        // The sensor being replaced is far away, the new one is right here. With the code on screen
        // the near sensor now ranks first…
        assertThat(
            OnePlusAdvCandidate.rankScore("DX02aS", STORED_MAC, -90, hint),
        ).isLessThan(
            OnePlusAdvCandidate.rankScore("DX02zz", OTHER_MAC, -50, hint),
        )
        // …while ranking with the store, which is what the screen used to do, put it last.
        assertThat(
            OnePlusAdvCandidate.rankScore("DX02aS", STORED_MAC, -90, stored),
        ).isGreaterThan(
            OnePlusAdvCandidate.rankScore("DX02zz", OTHER_MAC, -50, stored),
        )
    }

    private fun storedSensor(pin: String, serial: String? = null) = OnePlusStoredSession(
        identity = OnePlusSensorIdentity(pin = pin, serial = serial),
        lastMac = STORED_MAC,
        lastDeviceName = "DX02aS",
    )

    private companion object {

        const val STORED_MAC = "AA:BB:CC:DD:EE:FF"
        const val OTHER_MAC = "11:22:33:44:55:66"
    }
}
