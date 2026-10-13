package app.aaps.pump.diaconn

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DiaconnG8PumpTest : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync

    private lateinit var sut: DiaconnG8Pump

    @BeforeEach
    fun setup() {
        sut = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun pumpUidGenerationTest() {
        sut.country = 75 // 'K'
        sut.productType = 71 // 'G'
        sut.makeYear = 22
        sut.makeMonth = 6
        sut.makeDay = 15
        sut.lotNo = 123
        sut.serialNo = 12345

        val expectedUid = "75-71-22-06-15-123-12345"
        assertThat(sut.pumpUid).isEqualTo(expectedUid)
    }

    @Test
    fun pumpVersionTest() {
        sut.majorVersion = 3
        sut.minorVersion = 42

        assertThat(sut.pumpVersion).isEqualTo("3.42")
    }

    @Test
    fun tempBasalInProgressTest() {
        // No temp basal
        assertThat(sut.isTempBasalInProgress).isFalse()

        // Active temp basal
        sut.tempBasalStart = dateUtil.now() - 1000 * 60 * 10 // 10 minutes ago
        sut.tempBasalDuration = 1000 * 60 * 30 // 30 minutes duration
        sut.tempBasalAbsoluteRate = 1.5

        assertThat(sut.isTempBasalInProgress).isTrue()
        assertThat(sut.tempBasalRemainingMin).isGreaterThan(0)

        // Expired temp basal
        sut.tempBasalStart = dateUtil.now() - 1000 * 60 * 40 // 40 minutes ago
        sut.tempBasalDuration = 1000 * 60 * 30 // 30 minutes duration

        assertThat(sut.isTempBasalInProgress).isFalse()
        assertThat(sut.tempBasalRemainingMin).isEqualTo(0)
    }

    @Test
    fun extendedBolusInProgressTest() {
        // No extended bolus
        assertThat(sut.isExtendedInProgress).isFalse()

        // Active extended bolus
        sut.extendedBolusStart = dateUtil.now() - 1000 * 60 * 10 // 10 minutes ago
        sut.extendedBolusDuration = 1000 * 60 * 60 // 60 minutes duration
        sut.extendedBolusAmount = 3.0

        assertThat(sut.isExtendedInProgress).isTrue()
        assertThat(sut.extendedBolusRemainingMinutes).isGreaterThan(0)
        assertThat(sut.extendedBolusPassedMinutes).isGreaterThan(0)

        // Expired extended bolus
        sut.extendedBolusStart = dateUtil.now() - 1000 * 60 * 70 // 70 minutes ago
        sut.extendedBolusDuration = 1000 * 60 * 60 // 60 minutes duration

        assertThat(sut.isExtendedInProgress).isFalse()
        assertThat(sut.extendedBolusRemainingMinutes).isEqualTo(0)
    }

    @Test
    fun fromTemporaryBasalTest() {
        // Setting temp basal from PumpSync state
        val tbr = PumpSync.PumpState.TemporaryBasal(
            timestamp = dateUtil.now() - 1000 * 60 * 5,
            duration = 1000 * 60 * 30,
            rate = 2.0,
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.NORMAL,
            id = 1L,
            pumpId = 100L
        )

        sut.fromTemporaryBasal(tbr)
        assertThat(sut.tempBasalStart).isEqualTo(tbr.timestamp)
        assertThat(sut.tempBasalDuration).isEqualTo(tbr.duration)
        assertThat(sut.tempBasalAbsoluteRate).isEqualTo(tbr.rate)

        // Clearing temp basal
        sut.fromTemporaryBasal(null)
        assertThat(sut.tempBasalStart).isEqualTo(0)
        assertThat(sut.tempBasalDuration).isEqualTo(0)
        assertThat(sut.tempBasalAbsoluteRate).isEqualTo(0.0)
    }

    @Test
    fun fromExtendedBolusTest() {
        // Setting extended bolus from PumpSync state
        val eb = PumpSync.PumpState.ExtendedBolus(
            timestamp = dateUtil.now() - 1000 * 60 * 10,
            duration = 1000 * 60 * 90,
            amount = 4.5,
            rate = 3.0 // 4.5U over 90 minutes = 3.0 U/h
        )

        sut.fromExtendedBolus(eb)
        assertThat(sut.extendedBolusStart).isEqualTo(eb.timestamp)
        assertThat(sut.extendedBolusDuration).isEqualTo(eb.duration)
        assertThat(sut.extendedBolusAmount).isEqualTo(eb.amount)

        // Clearing extended bolus
        sut.fromExtendedBolus(null)
        assertThat(sut.extendedBolusStart).isEqualTo(0)
        assertThat(sut.extendedBolusDuration).isEqualTo(0)
        assertThat(sut.extendedBolusAmount).isEqualTo(0.0)
    }

    @Test
    fun resetTest() {
        sut.lastConnection = dateUtil.now()
        sut.lastSettingsRead = dateUtil.now()

        sut.reset()

        assertThat(sut.lastConnection).isEqualTo(0)
        assertThat(sut.lastSettingsRead).isEqualTo(0)
    }

    @Test
    fun buildDiaconnG8ProfileRecordTest() {
        val profile = validProfile
        val record = sut.buildDiaconnG8ProfileRecord(profile)

        assertThat(record.size).isEqualTo(24)
        // Verify all values are positive
        for (i in 0..23) {
            assertThat(record[i]).isAtLeast(0.0)
        }
    }

    @Test
    fun temporaryBasalToStringTest() {
        // No temp basal active
        assertThat(sut.temporaryBasalToString()).isEmpty()

        // Active temp basal
        sut.tempBasalStart = dateUtil.now() - 1000 * 60 * 10 // 10 minutes ago
        sut.tempBasalDuration = 1000 * 60 * 30 // 30 minutes duration
        sut.tempBasalAbsoluteRate = 1.5

        val result = sut.temporaryBasalToString()
        assertThat(result).contains("1.5")
        assertThat(result).contains("U/h")
    }

    @Test
    fun extendedBolusToStringTest() {
        // No extended bolus active
        assertThat(sut.extendedBolusToString()).isEmpty()

        // Active extended bolus
        sut.extendedBolusStart = dateUtil.now() - 1000 * 60 * 10 // 10 minutes ago
        sut.extendedBolusDuration = 1000 * 60 * 60 // 60 minutes duration
        sut.extendedBolusAmount = 3.0

        val result = sut.extendedBolusToString()
        assertThat(result).contains("E ")
        assertThat(result).contains("U/h")
    }

    @Test
    fun extendedBolusAbsoluteRateTest() {
        sut.extendedBolusStart = dateUtil.now()
        sut.extendedBolusDuration = 1000 * 60 * 60 // 60 minutes
        sut.extendedBolusAmount = 3.0 // 3 units over 60 minutes

        // Rate should be 3.0 U/h
        assertThat(sut.extendedBolusAbsoluteRate).isWithin(0.01).of(3.0)

        // Test setting rate
        sut.extendedBolusAbsoluteRate = 2.0
        assertThat(sut.extendedBolusAmount).isWithin(0.01).of(2.0)
    }
}
