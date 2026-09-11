package app.aaps.plugins.calibration

import app.aaps.core.data.model.CAL
import app.aaps.core.data.time.T
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CalibrationMathTest {

    private val now: Long = 1_700_000_000_000L

    @Test
    fun fitLinearCalibration_zeroEntries_returnsNull() {
        assertThat(fitLinearCalibration(emptyList(), now)).isNull()
    }

    @Test
    fun fitLinearCalibration_oneEntry_returnsNull() {
        assertThat(fitLinearCalibration(listOf(entry(140.0, 145.0)), now)).isNull()
    }

    @Test
    fun fitLinearCalibration_wideSpread_returnsFullMode() {
        // 100 mg/dL spread — well above MIN_SENSOR_RANGE_FOR_SLOPE (54)
        val fit = fitLinearCalibration(
            listOf(entry(100.0, 110.0), entry(200.0, 220.0)),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.Full)
        assertThat(fit.slope).isWithin(0.001).of(1.1)
        assertThat(fit.offset).isWithin(0.5).of(0.0)
    }

    @Test
    fun fitLinearCalibration_narrowSpread_returnsOffsetOnly() {
        // 4 mg/dL spread — well below MIN_SENSOR_RANGE_FOR_SLOPE
        val fit = fitLinearCalibration(
            listOf(entry(140.0, 145.0), entry(144.0, 148.0)),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.OffsetOnly)
        assertThat(fit.slope).isEqualTo(1.0)
    }

    @Test
    fun fitLinearCalibration_atThreshold_returnsFullMode() {
        // Exactly at threshold (54 mg/dL spread) — still trusts slope.
        val fit = fitLinearCalibration(
            listOf(entry(120.0, 120.0), entry(174.0, 174.0)),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.Full)
    }

    @Test
    fun fitLinearCalibration_futureDatedEntry_doesNotProduceNaN() {
        // Entry timestamp 5 minutes in the future (clock skew). Without the guard,
        // exp(+large) → Inf → NaN slope/offset. With the guard, weight = 1.0 and the
        // entry contributes normally.
        val fit = fitLinearCalibration(
            listOf(
                entry(100.0, 110.0, ageDays = 0L),
                CAL(
                    id = 0L,
                    timestamp = now + T.mins(5).msecs(),
                    fingerstickMgdl = 220.0,
                    sensorMgdlAtPairing = 200.0
                )
            ),
            now
        )!!
        assertThat(fit.slope).isFinite()
        assertThat(fit.offset).isFinite()
        assertThat(fit.slope).isWithin(0.001).of(1.1)
    }

    @Test
    fun fitLinearCalibration_veryOldEntry_isHeavilyDownweighted() {
        // One fresh entry on y = x, one ancient entry (10τ ≈ 20 days) heavily off.
        // The old entry should barely influence the fit due to exp(-10) ≈ 4.5e-5.
        val fit = fitLinearCalibration(
            listOf(
                entry(100.0, 100.0, ageDays = 0L),
                entry(200.0, 200.0, ageDays = 0L),
                entry(150.0, 999.0, ageDays = 20L)
            ),
            now
        )!!
        // Slope should still be ~1.0 despite the ancient outlier.
        assertThat(fit.slope).isWithin(0.05).of(1.0)
    }

    @Test
    fun fitLinearCalibration_slopeAboveMax_clampsAndRefitsOffset() {
        // Two Syai-style entries — free slope ≈ 1.72, above SLOPE_MAX = 1.6.
        // Expected: slope clamped to 1.6, offset recomputed = mean(y) − 1.6·mean(x) = 140.4 − 195.84 ≈ −55.44.
        val fit = fitLinearCalibration(
            listOf(entry(sensor = 72.0, fs = 54.0), entry(sensor = 172.8, fs = 226.8)),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.SlopeClamped)
        assertThat(fit.slope).isEqualTo(1.6)
        assertThat(fit.offset).isWithin(0.1).of(-55.44)
        assertThat(fit.isApplicable).isTrue()
        // Correction at center after clamping: (1.6−1)·100 + (−55.44) ≈ +4.56 mg/dL
        assertThat(fit.correctionAtCenter).isWithin(0.1).of(4.56)
    }

    @Test
    fun fitLinearCalibration_slopeBelowMin_clampsAndRefitsOffset() {
        // Entries imply slope ≈ 0.4 (sensor exaggerates). Clamp to SLOPE_MIN = 0.55, re-fit.
        val fit = fitLinearCalibration(
            listOf(entry(sensor = 100.0, fs = 80.0), entry(sensor = 200.0, fs = 120.0)),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.SlopeClamped)
        assertThat(fit.slope).isEqualTo(0.55)
    }

    @Test
    fun calibrationFit_correctionAtCenter_matchesLineEvaluatedAtCenter() {
        // Line y = 1.5·x − 54. At sensor=100: y = 150 − 54 = 96; correction = −4.
        val fit = CalibrationFit(slope = 1.5, offset = -54.0)
        assertThat(fit.correctionAtCenter).isWithin(0.001).of(-4.0)
        assertThat(fit.isApplicable).isTrue() // slope ∈ [0.55, 1.6] and correction ∈ [−30, 30]
    }

    @Test
    fun calibrationFit_steepSlopeWithLargeOffset_clampedByCorrectionNotOffset() {
        // Old clamp on offset would reject offset = −270 instantly; new clamp on
        // correction-at-center sees correction = (2.87 − 1)·100 + (−270) = −83, still rejected
        // because slope is also out of range — but the rejection reason is now meaningful.
        val fit = CalibrationFit(slope = 2.87, offset = -270.0)
        assertThat(fit.slopeInRange).isFalse()
        assertThat(fit.correctionInRange).isFalse()
        assertThat(fit.correctionAtCenter).isWithin(0.5).of(-83.0)
    }

    @Test
    fun fitLinearCalibration_negativeDeltas_handledCorrectly() {
        // Sensor reads HIGHER than fingerstick — negative offset.
        val fit = fitLinearCalibration(
            listOf(entry(120.0, 110.0), entry(220.0, 210.0)),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.Full)
        assertThat(fit.slope).isWithin(0.001).of(1.0)
        assertThat(fit.offset).isWithin(0.5).of(-10.0)
    }

    @Test
    fun fitLinearCalibration_offsetOnly_isWeightedMeanDelta() {
        // Five entries at the same timestamp (equal weights). Deltas: 2, 3, 4, 5, 6 → mean 4.
        val fit = fitLinearCalibration(
            listOf(
                entry(140.0, 142.0), entry(141.0, 144.0), entry(142.0, 146.0),
                entry(143.0, 148.0), entry(144.0, 150.0)
            ),
            now
        )!!
        assertThat(fit.mode).isEqualTo(FitMode.OffsetOnly)
        assertThat(fit.offset).isWithin(0.01).of(4.0)
    }

    private fun entry(sensor: Double, fs: Double, ageDays: Long = 0L): CAL =
        CAL(
            id = 0L,
            timestamp = now - T.days(ageDays).msecs(),
            fingerstickMgdl = fs,
            sensorMgdlAtPairing = sensor
        )
}
