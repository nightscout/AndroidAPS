package app.aaps.pump.carelevo.common

import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotFlags
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotResponse
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotTier
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.Test

internal class ActiveAlarmSnapshotAlarmMapperTest {

    private val mapper = ActiveAlarmSnapshotAlarmMapper()

    private fun patchInfo() = CarelevoPatchInfoDomainModel(
        address = "aa:bb:cc:dd:ee:ff",
        createdAt = DateTime.now().minusHours(1),
        updatedAt = DateTime.now(),
        manufactureNumber = "CARELEVO-TEST-001",
        insulinRemain = 60.0,
        thresholdExpiry = 24,
        bolusActionSeq = 1,
        mode = 1
    )

    @Test
    fun `map prefers higher priority tier for duplicate flags`() {
        val alarms = mapper.map(
            snapshots = listOf(
                ActiveAlarmSnapshotResponse(
                    tier = ActiveAlarmSnapshotTier.CRITICAL,
                    infusing = false,
                    flags = ActiveAlarmSnapshotFlags(
                        outOfInsulin = true,
                        operatingLifeExpired = false,
                        lowBattery = false,
                        outOfRangeTemperature = false,
                        autoOff = false,
                        unconnectedBle = false,
                        patchAppIncomplete = false,
                        startInsulin = false,
                        selfDiagnosisFailed = false,
                        patchExpired = false,
                        patchError = false,
                        occlusionDetected = false,
                        userForcedTermination = false
                    )
                ),
                ActiveAlarmSnapshotResponse(
                    tier = ActiveAlarmSnapshotTier.ADVISORY,
                    infusing = false,
                    flags = ActiveAlarmSnapshotFlags(
                        outOfInsulin = true,
                        operatingLifeExpired = false,
                        lowBattery = false,
                        outOfRangeTemperature = false,
                        autoOff = false,
                        unconnectedBle = false,
                        patchAppIncomplete = false,
                        startInsulin = false,
                        selfDiagnosisFailed = false,
                        patchExpired = false,
                        patchError = false,
                        occlusionDetected = false,
                        userForcedTermination = false
                    )
                )
            ),
            currentPatch = patchInfo(),
            now = "2026-08-12T10:00:00"
        )

        assertThat(alarms).hasSize(1)
        // CRITICAL wins the de-dupe, and resolves through the same axes as a live 0xA1 report:
        // tier -> AlarmType.WARNING, slot -> CAUSE 0x01. (The enum's WARNING/ALERT insulin names are
        // swapped relative to the protocol wording; that is pre-existing and shared with the live
        // path, so snapshot and live stay consistent.)
        assertThat(alarms.single().cause).isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN)
        assertThat(alarms.single().value).isEqualTo(60)
    }

    @Test
    fun `map resolves per-tier alarm causes and snapshot-backed values`() {
        val alarms = mapper.map(
            snapshots = listOf(
                ActiveAlarmSnapshotResponse(
                    tier = ActiveAlarmSnapshotTier.ADVISORY,
                    infusing = true,
                    flags = ActiveAlarmSnapshotFlags(
                        outOfInsulin = false,
                        operatingLifeExpired = true,
                        lowBattery = false,
                        outOfRangeTemperature = false,
                        autoOff = false,
                        unconnectedBle = false,
                        patchAppIncomplete = false,
                        startInsulin = true,
                        selfDiagnosisFailed = false,
                        patchExpired = false,
                        patchError = false,
                        occlusionDetected = false,
                        userForcedTermination = false
                    )
                ),
                ActiveAlarmSnapshotResponse(
                    tier = ActiveAlarmSnapshotTier.CRITICAL,
                    infusing = true,
                    flags = ActiveAlarmSnapshotFlags(
                        outOfInsulin = false,
                        operatingLifeExpired = false,
                        lowBattery = true,
                        outOfRangeTemperature = false,
                        autoOff = false,
                        unconnectedBle = false,
                        patchAppIncomplete = false,
                        startInsulin = false,
                        selfDiagnosisFailed = false,
                        patchExpired = false,
                        patchError = false,
                        occlusionDetected = false,
                        userForcedTermination = false
                    )
                )
            ),
            currentPatch = patchInfo(),
            now = "2026-08-12T10:00:00"
        )

        // Each cause is (tier -> AlarmType, slot -> CAUSE byte):
        //   ADVISORY + operatingLifeExpired -> (ALERT, 0x02) — the 0x02 cause, not 0x0a
        //   ADVISORY + startInsulin         -> (ALERT, 0x08)
        //   CRITICAL + lowBattery           -> (WARNING, 0x03) — matches a live 0xA1 report
        assertThat(alarms.map { it.cause }).containsExactly(
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
            AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT,
            AlarmCause.ALARM_WARNING_LOW_BATTERY
        )
        assertThat(alarms.first { it.cause == AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2 }.value).isEqualTo(24)
    }
}
