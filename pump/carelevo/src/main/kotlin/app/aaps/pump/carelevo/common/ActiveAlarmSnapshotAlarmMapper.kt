package app.aaps.pump.carelevo.common

import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotFlags
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotResponse
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotTier
import app.aaps.pump.carelevo.ble.commands.SnapshotAlarmFlag
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import java.util.LinkedHashMap
import java.util.UUID
import javax.inject.Inject

class ActiveAlarmSnapshotAlarmMapper @Inject constructor() {

    fun map(
        snapshots: List<ActiveAlarmSnapshotResponse>,
        currentPatch: CarelevoPatchInfoDomainModel?,
        now: String
    ): List<CarelevoAlarmInfo> {
        val byFlag = LinkedHashMap<SnapshotAlarmFlag, AlarmCause>()
        snapshots.forEach { snapshot ->
            snapshot.flags.toActiveFlags().forEach { flag ->
                if (byFlag.containsKey(flag)) return@forEach
                mapSnapshotFlagToCause(snapshot.tier, flag)?.let { byFlag[flag] = it }
            }
        }

        return byFlag.map { (flag, cause) ->
            CarelevoAlarmInfo(
                alarmId = UUID.randomUUID().toString(),
                alarmType = cause.alarmType,
                cause = cause,
                value = snapshotValueFor(flag, currentPatch),
                createdAt = now,
                updatedAt = now,
                isAcknowledged = false
            )
        }
    }

    private fun ActiveAlarmSnapshotFlags.toActiveFlags(): List<SnapshotAlarmFlag> = buildList {
        if (outOfInsulin) add(SnapshotAlarmFlag.OUT_OF_INSULIN)
        if (operatingLifeExpired) add(SnapshotAlarmFlag.OPERATING_LIFE_EXPIRED)
        if (lowBattery) add(SnapshotAlarmFlag.LOW_BATTERY)
        if (outOfRangeTemperature) add(SnapshotAlarmFlag.OUT_OF_RANGE_TEMPERATURE)
        if (autoOff) add(SnapshotAlarmFlag.AUTO_OFF)
        if (unconnectedBle) add(SnapshotAlarmFlag.UNCONNECTED_BLE)
        if (patchAppIncomplete) add(SnapshotAlarmFlag.PATCH_APP_INCOMPLETE)
        if (startInsulin) add(SnapshotAlarmFlag.START_INSULIN)
        if (selfDiagnosisFailed) add(SnapshotAlarmFlag.SELF_DIAGNOSIS_FAILED)
        if (patchExpired) add(SnapshotAlarmFlag.PATCH_EXPIRED)
        if (patchError) add(SnapshotAlarmFlag.PATCH_ERROR)
        if (occlusionDetected) add(SnapshotAlarmFlag.OCCLUSION_DETECTED)
        if (userForcedTermination) add(SnapshotAlarmFlag.USER_FORCED_TERMINATION)
    }

    /**
     * The snapshot tier IS the severity — 0xA4/0xA5/0xA6 mirror the pushed-alarm reports
     * 0xA1/0xA2/0xA3 one-for-one — so resolution goes through the same two axes the live path uses
     * in `CarelevoPatch.raiseAlarm`: the tier picks the [AlarmType], the slot's `CAUSE` byte picks
     * the cause within it. A reconnect snapshot therefore yields the identical [AlarmCause] the live
     * alarm would have produced, instead of a hand-maintained tier×flag table that can drift.
     *
     * Returns null when the pair has no counterpart: a slot with no `CAUSE` byte
     * (`USER_FORCED_TERMINATION`), or a (tier, cause) combination [AlarmCause] does not define — in
     * which case [AlarmCause.fromTypeAndCode] falls back to `ALARM_UNKNOWN`, which must not be
     * surfaced to the user as an alarm.
     */
    private fun mapSnapshotFlagToCause(
        tier: ActiveAlarmSnapshotTier,
        flag: SnapshotAlarmFlag
    ): AlarmCause? {
        val causeCode = flag.causeCode ?: return null
        return AlarmCause.fromTypeAndCode(tier.toAlarmType(), causeCode)
            .takeIf { it != AlarmCause.ALARM_UNKNOWN }
    }

    /** 0xA4/0xA5/0xA6 carry the same severities as the pushed 0xA1/0xA2/0xA3 reports. */
    private fun ActiveAlarmSnapshotTier.toAlarmType(): AlarmType = when (this) {
        ActiveAlarmSnapshotTier.CRITICAL     -> AlarmType.WARNING
        ActiveAlarmSnapshotTier.ADVISORY     -> AlarmType.ALERT
        ActiveAlarmSnapshotTier.NOTIFICATION -> AlarmType.NOTICE
    }

    /**
     * The pushed-alarm reports carry a `VALUE` byte alongside `CAUSE`; the snapshot does not, so the
     * equivalent is reconstructed from current patch state.
     *
     * Keyed on the slot rather than the resolved [AlarmCause] on purpose: which quantity a slot
     * means (insulin left vs. time left) is a property of the slot, and does not change with the
     * tier. Matching on cause names instead would have to re-list every per-tier cause and would
     * silently fall through to null the moment a tier mapping changes.
     */
    private fun snapshotValueFor(
        flag: SnapshotAlarmFlag,
        currentPatch: CarelevoPatchInfoDomainModel?
    ): Int? = when (flag) {
        SnapshotAlarmFlag.OUT_OF_INSULIN -> currentPatch?.insulinRemain?.toInt()

        SnapshotAlarmFlag.OPERATING_LIFE_EXPIRED,
        SnapshotAlarmFlag.PATCH_EXPIRED  -> currentPatch?.thresholdExpiry

        else                             -> null
    }
}
