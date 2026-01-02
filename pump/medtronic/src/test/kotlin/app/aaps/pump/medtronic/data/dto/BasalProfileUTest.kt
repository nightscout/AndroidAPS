package app.aaps.pump.medtronic.data.dto

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.medtronic.MedtronicTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Created by andy on 6/16/18.
 * Updated to JUnit 5 / Kotlin
 */
class BasalProfileUTest : MedtronicTestBase() {

    @BeforeEach
    fun setup() {
        super.initializeCommonMocks()
    }

    @Test
    fun `test getProfilesByHour with complex basal profile`() {
        val pumpType = PumpType.MEDTRONIC_522_722

        val data = byteArrayOf(
            0x48.toByte(), 0x00, 0x00, 0x40.toByte(), 0x00, 0x02, 0x38, 0x00, 0x04, 0x3A, 0x00, 0x06, 0x32, 0x00, 0x0C, 0x26, 0x00,
            0x10, 0x2E, 0x00, 0x14, 0x32, 0x00, 0x18, 0x26, 0x00, 0x1A, 0x1A, 0x00, 0x20, 0x14, 0x00, 0x2A, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        val basalProfile = BasalProfile(aapsLogger, data)
        val profilesByHour = basalProfile.getProfilesByHour(pumpType)

        // Verify expected basal rates for each hour
        assertThat(profilesByHour[0]).isWithin(0.01).of(1.8)
        assertThat(profilesByHour[1]).isWithin(0.01).of(1.6)
        assertThat(profilesByHour[2]).isWithin(0.01).of(1.4)
        assertThat(profilesByHour[3]).isWithin(0.01).of(1.45)
        assertThat(profilesByHour[4]).isWithin(0.01).of(1.45)
        assertThat(profilesByHour[5]).isWithin(0.01).of(1.45)
        assertThat(profilesByHour[6]).isWithin(0.01).of(1.25)
        assertThat(profilesByHour[7]).isWithin(0.01).of(1.25)
        assertThat(profilesByHour[8]).isWithin(0.01).of(0.95)
        assertThat(profilesByHour[9]).isWithin(0.01).of(0.95)
        assertThat(profilesByHour[10]).isWithin(0.01).of(1.15)
        assertThat(profilesByHour[11]).isWithin(0.01).of(1.15)
        assertThat(profilesByHour[12]).isWithin(0.01).of(1.25)
        assertThat(profilesByHour[13]).isWithin(0.01).of(0.95)
        assertThat(profilesByHour[14]).isWithin(0.01).of(0.95)
        assertThat(profilesByHour[15]).isWithin(0.01).of(0.95)
        assertThat(profilesByHour[16]).isWithin(0.01).of(0.65)
        assertThat(profilesByHour[17]).isWithin(0.01).of(0.65)
        assertThat(profilesByHour[18]).isWithin(0.01).of(0.65)
        assertThat(profilesByHour[19]).isWithin(0.01).of(0.65)
        assertThat(profilesByHour[20]).isWithin(0.01).of(0.65)
        assertThat(profilesByHour[21]).isWithin(0.01).of(0.5)
        assertThat(profilesByHour[22]).isWithin(0.01).of(0.5)
        assertThat(profilesByHour[23]).isWithin(0.01).of(0.5)
    }

    @Test
    fun `test simple basal profile with two rates`() {
        val pumpType = PumpType.MEDTRONIC_522_722

        val data = byteArrayOf(
            0x32, 0x00, 0x00, 0x2C, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        val basalProfile = BasalProfile(aapsLogger, data)
        val profilesByHour = basalProfile.getProfilesByHour(pumpType)

        // Should have valid entries (rate 0x0032 = 50 / 40 = 1.25U, rate 0x002C = 44 / 40 = 1.1U)
        assertThat(profilesByHour).hasLength(24)
        assertThat(profilesByHour[0]).isWithin(0.01).of(1.25)  // From midnight to 6am
        assertThat(profilesByHour[6]).isWithin(0.01).of(1.1)   // From 6am onwards
    }

    @Test
    fun `test empty basal profile with single zero entry`() {
        val pumpType = PumpType.MEDTRONIC_522_722
        val data = byteArrayOf(0x00)

        val basalProfile = BasalProfile(aapsLogger, data)
        val profilesByHour = basalProfile.getProfilesByHour(pumpType)

        // Empty profile should be expanded to [0, 0, 0] which results in 24 hours of 0.0
        assertThat(profilesByHour).hasLength(24)
        assertThat(profilesByHour[0]).isEqualTo(0.0)
    }

    @Test
    fun `test empty basal profile with terminator`() {
        val pumpType = PumpType.MEDTRONIC_522_722
        val data = byteArrayOf(0x00, 0x00, 0x3f)

        val basalProfile = BasalProfile(aapsLogger, data)
        val profilesByHour = basalProfile.getProfilesByHour(pumpType)

        // Terminator pattern should result in empty entries, filled with zeros
        assertThat(profilesByHour).hasLength(24)
        assertThat(BasalProfile.isBasalProfileByHourUndefined(profilesByHour)).isTrue()
    }

    @Test
    fun `test basal profile from history format`() {
        val pumpType = PumpType.MEDTRONIC_522_722

        val data = byteArrayOf(
            0, 72, 0, 2, 64, 0, 4, 56, 0, 6, 58, 0, 8, 58, 0, 10, 58, 0, 12, 50, 0, 14, 50, 0, 16, 38, 0, 18, 38, 0,
            20, 46, 0, 22, 46, 0, 24, 50, 0, 26, 38, 0, 28, 38, 0, 30, 38, 0, 32, 26, 0, 34, 26, 0, 36, 26, 0, 38, 26,
            0, 40, 26, 0, 42, 20, 0, 44, 20, 0, 46, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        val basalProfile = BasalProfile(aapsLogger)
        basalProfile.setRawDataFromHistory(data)

        val profilesByHour = basalProfile.getProfilesByHour(pumpType)

        // Expected profile from comment in original test:
        // 1.800 1.600 1.400 1.450 1.450 1.450 1.250 1.250 0.950 0.950 1.150 1.150 1.250 0.950 0.950 0.950 0.650 0.650
        // 0.650 0.650 0.650 0.500 0.500 0.500
        assertThat(profilesByHour[0]).isWithin(0.01).of(1.8)
        assertThat(profilesByHour[1]).isWithin(0.01).of(1.6)
        assertThat(profilesByHour[2]).isWithin(0.01).of(1.4)
        assertThat(profilesByHour[21]).isWithin(0.01).of(0.5)
        assertThat(profilesByHour[23]).isWithin(0.01).of(0.5)
    }

    @Test
    fun `test verify accepts valid basal profile`() {
        val pumpType = PumpType.MEDTRONIC_522_722

        val data = byteArrayOf(
            0x32, 0x00, 0x00, 0x2C, 0x00, 0x0C, 0x00, 0x00, 0x00
        )

        val basalProfile = BasalProfile(aapsLogger, data)

        // Valid profiles (under 35 U/hr) should verify successfully
        assertThat(basalProfile.verify(pumpType)).isTrue()
    }

    @Test
    fun `test basal profile entries are correctly parsed`() {
        val data = byteArrayOf(
            0x32, 0x00, 0x00,  // Entry 1: rate=0x0032 (50), start=0x00 (00:00)
            0x2C, 0x00, 0x0C,  // Entry 2: rate=0x002C (44), start=0x0C (06:00)
            0x00, 0x00, 0x00   // Terminator
        )

        val basalProfile = BasalProfile(aapsLogger, data)
        val entries = basalProfile.getEntries()

        assertThat(entries).hasSize(2)
        assertThat(entries[0].rate).isWithin(0.01).of(1.25)  // 50 / 40 = 1.25
        assertThat(entries[1].rate).isWithin(0.01).of(1.1)   // 44 / 40 = 1.1
    }
}
