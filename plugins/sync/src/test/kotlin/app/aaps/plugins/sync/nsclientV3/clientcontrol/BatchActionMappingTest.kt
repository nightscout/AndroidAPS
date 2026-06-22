package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.nssdk.localmodel.clientcontrol.BatchActionDto
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Round-trip checks for the wire mapping ([toDto] → [toDomain]) — guards against a field-mapping typo on the
 * client→master path (e.g. dropping [BatchAction.TempBasal.isPercent], which would silently flip percent↔absolute).
 */
class BatchActionMappingTest {

    @Test
    fun tempBasalPercentRoundTrips() {
        val original = BatchAction.TempBasal(rate = 150.0, isPercent = true, durationMinutes = 30)
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun tempBasalAbsoluteRoundTrips() {
        val original = BatchAction.TempBasal(rate = 1.25, isPercent = false, durationMinutes = 45)
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun extendedBolusRoundTrips() {
        val original = BatchAction.ExtendedBolus(insulin = 1.5, durationMinutes = 120)
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun runningModeStillRoundTrips() {
        val original = BatchAction.RunningMode(mode = RM.Mode.CLOSED_LOOP, durationMinutes = 0)
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun cancelTempBasalRoundTrips() {
        assertThat(BatchAction.CancelTempBasal.toDto().toDomain()).isEqualTo(BatchAction.CancelTempBasal)
    }

    @Test
    fun cancelExtendedBolusRoundTrips() {
        assertThat(BatchAction.CancelExtendedBolus.toDto().toDomain()).isEqualTo(BatchAction.CancelExtendedBolus)
    }

    @Test
    fun insulinActivateRoundTripsTheICfg() {
        val original = BatchAction.InsulinActivate(
            ICfg(insulinLabel = "Rapid", insulinEndTime = 360, insulinPeakTime = 75, concentration = 1.0).also { it.insulinNickname = "Rapid" }
        )
        val back = original.toDto().toDomain() as BatchAction.InsulinActivate
        assertThat(back.iCfg.insulinLabel).isEqualTo("Rapid")
        assertThat(back.iCfg.insulinPeakTime).isEqualTo(75L)
        assertThat(back.iCfg.insulinEndTime).isEqualTo(360L)
        assertThat(back.iCfg.concentration).isEqualTo(1.0)
        assertThat(back.iCfg.insulinNickname).isEqualTo("Rapid")
    }

    @Test
    fun insulinActivateWithUnparseableICfgMapsToNull() {
        // A malformed iCfgJson drops the action (prepareBatch's no-action guard then rejects an insulin-only batch).
        assertThat(BatchActionDto(type = BatchActionDto.TYPE_INSULIN_ACTIVATE, iCfgJson = "{not json").toDomain()).isNull()
    }
}
