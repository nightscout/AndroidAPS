package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
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
    fun bolusRoundTripsTheQuickWizardGuid() {
        // The guid lets the master mark the originating QuickWizard used on commit (SOT) — a dropped field would
        // silently revert to the client-side markAsUsed bug ("Update settings … Another action is already in progress").
        val original = BatchAction.Bolus(
            insulin = 1.5, carbs = 20, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
            recordOnly = false, notes = "Lunch", timestamp = 0L, iCfg = null, quickWizardGuid = "abc-123"
        )
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

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

    @Test
    fun therapyEventBgCheckRoundTrips() {
        val original = BatchAction.TherapyEvent(
            teType = TE.Type.FINGER_STICK_BG_VALUE,
            timestamp = 1_700_000_000_000L,
            glucoseMgdl = 110.0,
            glucoseType = TE.MeterType.FINGER,
            durationMinutes = 30,
            note = "after lunch",
            source = Sources.BgCheck
        )
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun therapyEventSiteChangeRoundTrips() {
        val original = BatchAction.TherapyEvent(
            teType = TE.Type.CANNULA_CHANGE,
            timestamp = 1_700_000_001_000L,
            location = TE.Location.SIDE_LEFT_UPPER_ARM,
            arrow = TE.Arrow.RIGHT,
            source = Sources.FillDialog
        )
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun therapyEventWithUnknownTypeMapsToNull() {
        // An unknown/unparseable teType drops the action (the master's no-action guard then rejects an empty batch).
        assertThat(BatchActionDto(type = BatchActionDto.TYPE_THERAPY_EVENT, teType = "NOT_A_TYPE").toDomain()).isNull()
    }

    @Test
    fun therapyEventEditRoundTrips() {
        val original = BatchAction.TherapyEventEdit(
            teType = TE.Type.CANNULA_CHANGE,
            timestamp = 1_700_000_002_000L,
            location = TE.Location.SIDE_LEFT_UPPER_ARM,
            arrow = TE.Arrow.RIGHT,
            note = "moved",
            source = Sources.SiteRotationDialog
        )
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun therapyEventEditWithUnknownTypeMapsToNull() {
        // An unknown/unparseable teType drops the action (the master's no-action guard then rejects an empty batch).
        assertThat(BatchActionDto(type = BatchActionDto.TYPE_THERAPY_EVENT_EDIT, teType = "NOT_A_TYPE").toDomain()).isNull()
    }
}
