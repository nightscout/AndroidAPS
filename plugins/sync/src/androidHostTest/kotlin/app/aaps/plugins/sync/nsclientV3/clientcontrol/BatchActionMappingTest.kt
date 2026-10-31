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

    /** The carbs dialog's "time to eat" timer is started by the master, so its flag must reach it. */
    @Test
    fun bolusRoundTripsTheEatReminderFlag() {
        val original = BatchAction.Bolus(
            insulin = 0.0, carbs = 30, carbsTimeOffsetMinutes = 20, carbsDurationHours = 0,
            recordOnly = false, notes = "", timestamp = 0L, iCfg = null, alarm = true
        )
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    /** An older client does not send the flag: no timer, as before. */
    @Test
    fun aBolusWithoutTheFlagHasNoEatReminder() {
        val fromOlderClient = BatchActionDto(type = BatchActionDto.TYPE_BOLUS, carbs = 30, carbsTimeOffsetMinutes = 20)
        assertThat((fromOlderClient.toDomain() as BatchAction.Bolus).alarm).isFalse()
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
    fun profileSwitchRoundTrips() {
        val original = BatchAction.ProfileSwitch(percentage = 110, timeShiftHours = 2, durationMinutes = 60, profileName = "Lunch", notes = "n")
        val back = original.toDto().toDomain() as BatchAction.ProfileSwitch
        assertThat(back.percentage).isEqualTo(110)
        assertThat(back.timeShiftHours).isEqualTo(2)
        assertThat(back.durationMinutes).isEqualTo(60)
        assertThat(back.profileName).isEqualTo("Lunch")
        assertThat(back.notes).isEqualTo("n")
        // No insulin asked of the user → the master resolves the one in force (or refuses).
        assertThat(back.iCfg).isNull()
        // No time picked on the client → 0, and the master stamps its own clock.
        assertThat(back.timestamp).isEqualTo(0L)
    }

    @Test
    fun profileSwitchRoundTripsThePickedTime() {
        // The time the user picked on a client has to reach the master, or a relayed switch is applied
        // immediately instead of at that time (issue #5239). The master bounds the value itself.
        val picked = 1_700_000_000_000L
        val original = BatchAction.ProfileSwitch(percentage = 100, timeShiftHours = 0, durationMinutes = 0, profileName = "Lunch", timestamp = picked)
        val back = original.toDto().toDomain() as BatchAction.ProfileSwitch
        assertThat(back.timestamp).isEqualTo(picked)
    }

    @Test
    fun profileSwitchRoundTripsTheICfgTheClientAskedTheUserFor() {
        val original = BatchAction.ProfileSwitch(
            percentage = 100, timeShiftHours = 0, durationMinutes = 0, profileName = "Lunch",
            iCfg = ICfg(insulinLabel = "Rapid", insulinEndTime = 360, insulinPeakTime = 75, concentration = 2.0).also { it.insulinNickname = "Pen" }
        )
        val back = original.toDto().toDomain() as BatchAction.ProfileSwitch
        assertThat(back.iCfg).isNotNull()
        assertThat(back.iCfg?.insulinLabel).isEqualTo("Rapid")
        assertThat(back.iCfg?.insulinPeakTime).isEqualTo(75L)
        assertThat(back.iCfg?.insulinEndTime).isEqualTo(360L)
        // Concentration must survive: a U200 pen recorded as U100 would halve every IOB calculation downstream.
        assertThat(back.iCfg?.concentration).isEqualTo(2.0)
        assertThat(back.iCfg?.insulinNickname).isEqualTo("Pen")
    }

    @Test
    fun profileSwitchWithUnparseableICfgKeepsTheSwitchAndDropsOnlyTheICfg() {
        // Unlike INSULIN_ACTIVATE, the switch is still meaningful without an insulin — the master resolves
        // the one in force, or refuses at prepare when there is none. Dropping the whole action would be worse.
        val back = BatchActionDto(
            type = BatchActionDto.TYPE_PROFILE_SWITCH, percentage = 100, profileName = "Lunch", iCfgJson = "{not json"
        ).toDomain() as BatchAction.ProfileSwitch
        assertThat(back.profileName).isEqualTo("Lunch")
        assertThat(back.iCfg).isNull()
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

    @Test
    fun removeSendsOnlyTheNightscoutIds() {
        // A local database id means nothing on the master, so it must not travel. A record without a Nightscout id
        // arrives as a ref without any id: the master finds nothing for it and counts it in its confirmation.
        val original = BatchAction.Remove(
            BatchAction.Remove.Kind.CARBS,
            listOf(BatchAction.Remove.Ref(nsId = "ns1", localId = 11L), BatchAction.Remove.Ref(nsId = null, localId = 12L))
        )

        val dto = original.toDto()

        assertThat(dto.nsIds).containsExactly("ns1")
        assertThat(dto.localOnlyCount).isEqualTo(1)
        assertThat(dto.toDomain()).isEqualTo(
            BatchAction.Remove(BatchAction.Remove.Kind.CARBS, listOf(BatchAction.Remove.Ref(nsId = "ns1"), BatchAction.Remove.Ref(nsId = null)))
        )
    }

    @Test
    fun removeOfEveryKindRoundTripsTheKind() {
        BatchAction.Remove.Kind.entries.forEach { kind ->
            val original = BatchAction.Remove(kind, listOf(BatchAction.Remove.Ref(nsId = "id")))
            assertThat(original.toDto().toDomain()).isEqualTo(original)
        }
    }

    @Test
    fun removeWithUnknownKindMapsToNull() {
        assertThat(BatchActionDto(type = BatchActionDto.TYPE_REMOVE, recordKind = "NOT_A_KIND", nsIds = listOf("a")).toDomain()).isNull()
    }

    @Test
    fun removeWithAHugeLocalOnlyCountIsBounded() {
        val removal = BatchActionDto(type = BatchActionDto.TYPE_REMOVE, recordKind = "BOLUS", localOnlyCount = Int.MAX_VALUE).toDomain() as BatchAction.Remove
        assertThat(removal.records.size).isAtMost(10_000)
    }

    @Test
    fun removeStartEventsRoundTrips() {
        assertThat(BatchAction.RemoveStartEvents.toDto().toDomain()).isEqualTo(BatchAction.RemoveStartEvents)
    }

    @Test
    fun calibrationRoundTrips() {
        val original = BatchAction.Calibration(glucoseMgdl = 123.0)
        assertThat(original.toDto().toDomain()).isEqualTo(original)
    }

    @Test
    fun calibrationWithoutGlucoseMapsToNull() {
        assertThat(BatchActionDto(type = BatchActionDto.TYPE_CALIBRATION).toDomain()).isNull()
    }
}
