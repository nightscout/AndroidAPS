package app.aaps.core.interfaces.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins [DetailedBolusInfo.copy] field by field.
 *
 * It copies fifteen properties by hand and had no test, which is how it came to silently drop
 * `bolusTimestamp` - a field [DetailedBolusInfo.createBolus] reads as `bolusTimestamp ?: timestamp`
 * to decide the timestamp of the stored bolus record. Nothing assigns that field today, so the loss
 * is currently invisible; the moment a driver does set it, a copied info would write the record at
 * the wrong time.
 *
 * A hand written copy needs a test that names every field, otherwise the next one added is dropped
 * the same way.
 */
class DetailedBolusInfoCopyTest {

    private fun populated() = DetailedBolusInfo().apply {
        insulin = 1.25
        carbs = 30.0
        timestamp = 1_700_000_000_000L
        lastKnownBolusTime = 1_699_999_000_000L
        deliverAtTheLatest = 1_700_000_060_000L
        eventType = TE.Type.CORRECTION_BOLUS
        notes = "note"
        mgdlGlucose = 123.0
        glucoseType = TE.MeterType.FINGER
        bolusType = BS.Type.SMB
        carbsDuration = 3_600_000L
        pumpType = PumpType.ACCU_CHEK_COMBO
        pumpSerial = "serial-1"
        bolusPumpId = 42L
        bolusTimestamp = 1_700_000_005_000L
        carbsTimestamp = 1_700_000_010_000L
    }

    @Test
    fun copy_carriesEveryField() {
        val original = populated()

        val copy = original.copy()

        assertThat(copy.insulin).isEqualTo(original.insulin)
        assertThat(copy.carbs).isEqualTo(original.carbs)
        assertThat(copy.timestamp).isEqualTo(original.timestamp)
        assertThat(copy.lastKnownBolusTime).isEqualTo(original.lastKnownBolusTime)
        assertThat(copy.deliverAtTheLatest).isEqualTo(original.deliverAtTheLatest)
        assertThat(copy.bolusCalculatorResult).isEqualTo(original.bolusCalculatorResult)
        assertThat(copy.eventType).isEqualTo(original.eventType)
        assertThat(copy.notes).isEqualTo(original.notes)
        assertThat(copy.mgdlGlucose).isEqualTo(original.mgdlGlucose)
        assertThat(copy.glucoseType).isEqualTo(original.glucoseType)
        assertThat(copy.bolusType).isEqualTo(original.bolusType)
        assertThat(copy.carbsDuration).isEqualTo(original.carbsDuration)
        assertThat(copy.pumpType).isEqualTo(original.pumpType)
        assertThat(copy.pumpSerial).isEqualTo(original.pumpSerial)
        assertThat(copy.bolusPumpId).isEqualTo(original.bolusPumpId)
        assertThat(copy.bolusTimestamp).isEqualTo(original.bolusTimestamp)
        assertThat(copy.carbsTimestamp).isEqualTo(original.carbsTimestamp)
    }

    @Test
    fun copy_isANewInstance() {
        val original = populated()

        val copy = original.copy()
        copy.insulin = 9.9

        assertThat(copy).isNotSameInstanceAs(original)
        assertThat(original.insulin).isEqualTo(1.25)
    }

    // There is deliberately no test that `copy()` leaves `id` behind. It cannot be asserted by value:
    // `id` is `Clock.System.now().toEpochMilliseconds()`, so an instance and its copy are built in the
    // same millisecond and get the SAME number - a fresh id is indistinguishable from a copied one.
    // Worth knowing on its own: two DetailedBolusInfo created in one millisecond share an id, which is
    // the same collision that WizardBolusExecutorImpl.nextPendingId() exists to avoid for parked doses.

    @Test
    fun createBolus_usesBolusTimestampWhenSet_afterACopy() {
        // The reason the missing field matters: this is what reads it.
        val copy = populated().copy()

        assertThat(copy.createBolus(iCfg = app.aaps.core.data.model.ICfg("", 0, 0)).timestamp)
            .isEqualTo(1_700_000_005_000L)
    }
}
