package app.aaps.pump.carelevo.data.model.entities

import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic coverage for the Gson (de)serialized Carelevo entity data classes.
 *
 * These are plain `data class` value objects, so the meaningful surface is the generated
 * constructor + defaults, `componentN` destructuring, `copy`, `equals`/`hashCode`, and `toString`.
 * Each branch of the generated `equals` is exercised by mutating a single field and asserting the
 * two instances are no longer equal.
 */
internal class CarelevoEntitiesTest {

    // ---------------------------------------------------------------------------------------------
    // CarelevoInfusionInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `InfusionInfo default constructor leaves every field null`() {
        val info = CarelevoInfusionInfoEntity()
        assertThat(info.basalInfusionInfo).isNull()
        assertThat(info.tempBasalInfusionInfo).isNull()
        assertThat(info.immeBolusInfusionInfo).isNull()
        assertThat(info.extendBolusInfusionInfo).isNull()
    }

    @Test
    fun `InfusionInfo holds the four nested infusion aggregates`() {
        val basal = CarelevoBasalInfusionInfoEntity(
            infusionId = "b1", address = "AA:BB", mode = 1, createdAt = "c", updatedAt = "u",
            segments = emptyList(), isStop = false
        )
        val temp = CarelevoTempBasalInfusionInfoEntity("t1", "AA:BB", 2, "c", "u")
        val imme = CarelevoImmeBolusInfusionInfoEntity("i1", "AA:BB", 3, "c", "u")
        val extend = CarelevoExtendBolusInfusionInfoEntity("e1", "AA:BB", 4, "c", "u")

        val info = CarelevoInfusionInfoEntity(basal, temp, imme, extend)

        assertThat(info.basalInfusionInfo).isSameInstanceAs(basal)
        assertThat(info.tempBasalInfusionInfo).isSameInstanceAs(temp)
        assertThat(info.immeBolusInfusionInfo).isSameInstanceAs(imme)
        assertThat(info.extendBolusInfusionInfo).isSameInstanceAs(extend)
    }

    @Test
    fun `InfusionInfo copy swaps a single member and keeps equality contract`() {
        val original = CarelevoInfusionInfoEntity()
        val temp = CarelevoTempBasalInfusionInfoEntity("t1", "AA:BB", 2, "c", "u")
        val copy = original.copy(tempBasalInfusionInfo = temp)

        assertThat(copy).isNotEqualTo(original)
        assertThat(copy.tempBasalInfusionInfo).isSameInstanceAs(temp)
        assertThat(copy.basalInfusionInfo).isNull()
        // Copying back to identical values restores equality + hashCode.
        assertThat(copy.copy(tempBasalInfusionInfo = null)).isEqualTo(original)
        assertThat(copy.copy(tempBasalInfusionInfo = null).hashCode()).isEqualTo(original.hashCode())
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoBasalSegmentInfusionInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `BasalSegment exposes all values and destructures in order`() {
        val seg = CarelevoBasalSegmentInfusionInfoEntity(
            createdAt = "created", updatedAt = "updated", startTime = 0, endTime = 60, speed = 1.25
        )
        val (createdAt, updatedAt, startTime, endTime, speed) = seg
        assertThat(createdAt).isEqualTo("created")
        assertThat(updatedAt).isEqualTo("updated")
        assertThat(startTime).isEqualTo(0)
        assertThat(endTime).isEqualTo(60)
        assertThat(speed).isEqualTo(1.25)
    }

    @Test
    fun `BasalSegment equals differs when the speed changes`() {
        val a = CarelevoBasalSegmentInfusionInfoEntity("c", "u", 0, 60, 1.0)
        val b = a.copy(speed = 2.0)
        assertThat(b).isNotEqualTo(a)
        assertThat(a.copy(speed = 1.0)).isEqualTo(a)
        assertThat(a.toString()).contains("speed=1.0")
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoBasalInfusionInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `BasalInfusion carries its segment list and stop flag`() {
        val seg = CarelevoBasalSegmentInfusionInfoEntity("c", "u", 0, 30, 0.5)
        val basal = CarelevoBasalInfusionInfoEntity(
            infusionId = "id-1", address = "AA:BB:CC", mode = 7, createdAt = "c", updatedAt = "u",
            segments = listOf(seg), isStop = true
        )
        assertThat(basal.infusionId).isEqualTo("id-1")
        assertThat(basal.address).isEqualTo("AA:BB:CC")
        assertThat(basal.mode).isEqualTo(7)
        assertThat(basal.segments).containsExactly(seg)
        assertThat(basal.isStop).isTrue()
    }

    @Test
    fun `BasalInfusion equals differs on isStop and on segments`() {
        val seg = CarelevoBasalSegmentInfusionInfoEntity("c", "u", 0, 30, 0.5)
        val base = CarelevoBasalInfusionInfoEntity("id", "A", 1, "c", "u", listOf(seg), false)
        assertThat(base.copy(isStop = true)).isNotEqualTo(base)
        assertThat(base.copy(segments = emptyList())).isNotEqualTo(base)
        assertThat(base.copy()).isEqualTo(base)
        assertThat(base.copy().hashCode()).isEqualTo(base.hashCode())
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoTempBasalInfusionInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `TempBasal optional fields default to null`() {
        val temp = CarelevoTempBasalInfusionInfoEntity(
            infusionId = "t", address = "A", mode = 1, createdAt = "c", updatedAt = "u"
        )
        assertThat(temp.percent).isNull()
        assertThat(temp.speed).isNull()
        assertThat(temp.infusionDurationMin).isNull()
    }

    @Test
    fun `TempBasal fully-populated retains all optional values`() {
        val temp = CarelevoTempBasalInfusionInfoEntity(
            "t", "A", 1, "c", "u", percent = 150, speed = 3.0, infusionDurationMin = 90
        )
        assertThat(temp.percent).isEqualTo(150)
        assertThat(temp.speed).isEqualTo(3.0)
        assertThat(temp.infusionDurationMin).isEqualTo(90)
        // equals distinguishes on each optional field.
        assertThat(temp.copy(percent = null)).isNotEqualTo(temp)
        assertThat(temp.copy(speed = 4.0)).isNotEqualTo(temp)
        assertThat(temp.copy(infusionDurationMin = 91)).isNotEqualTo(temp)
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoImmeBolusInfusionInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ImmeBolus optional fields default to null`() {
        val imme = CarelevoImmeBolusInfusionInfoEntity("i", "A", 1, "c", "u")
        assertThat(imme.volume).isNull()
        assertThat(imme.infusionDurationSeconds).isNull()
    }

    @Test
    fun `ImmeBolus retains volume and duration and distinguishes on them`() {
        val imme = CarelevoImmeBolusInfusionInfoEntity(
            "i", "A", 1, "c", "u", volume = 2.5, infusionDurationSeconds = 45
        )
        assertThat(imme.volume).isEqualTo(2.5)
        assertThat(imme.infusionDurationSeconds).isEqualTo(45)
        assertThat(imme.copy(volume = 2.6)).isNotEqualTo(imme)
        assertThat(imme.copy(infusionDurationSeconds = 46)).isNotEqualTo(imme)
        val (id, address, mode) = imme
        assertThat(id).isEqualTo("i")
        assertThat(address).isEqualTo("A")
        assertThat(mode).isEqualTo(1)
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoExtendBolusInfusionInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ExtendBolus optional fields default to null`() {
        val extend = CarelevoExtendBolusInfusionInfoEntity("e", "A", 1, "c", "u")
        assertThat(extend.volume).isNull()
        assertThat(extend.speed).isNull()
        assertThat(extend.infusionDurationMin).isNull()
    }

    @Test
    fun `ExtendBolus retains all values and distinguishes on each optional field`() {
        val extend = CarelevoExtendBolusInfusionInfoEntity(
            "e", "A", 1, "c", "u", volume = 5.0, speed = 1.0, infusionDurationMin = 120
        )
        assertThat(extend.volume).isEqualTo(5.0)
        assertThat(extend.speed).isEqualTo(1.0)
        assertThat(extend.infusionDurationMin).isEqualTo(120)
        assertThat(extend.copy(volume = 6.0)).isNotEqualTo(extend)
        assertThat(extend.copy(speed = 2.0)).isNotEqualTo(extend)
        assertThat(extend.copy(infusionDurationMin = 121)).isNotEqualTo(extend)
        assertThat(extend.copy()).isEqualTo(extend)
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoAlarmInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `AlarmInfo defaults value to null and occurrenceCount to one`() {
        val alarm = CarelevoAlarmInfoEntity(
            alarmId = "a1", alarmType = AlarmType.WARNING.code,
            cause = AlarmCause.ALARM_WARNING_LOW_INSULIN,
            createdAt = "c", updatedAt = "u", acknowledged = false
        )
        assertThat(alarm.value).isNull()
        assertThat(alarm.occurrenceCount).isEqualTo(1)
        assertThat(alarm.acknowledged).isFalse()
        assertThat(alarm.cause).isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN)
    }

    @Test
    fun `AlarmInfo carries an explicit value and occurrenceCount`() {
        val alarm = CarelevoAlarmInfoEntity(
            alarmId = "a2", alarmType = AlarmType.NOTICE.code,
            cause = AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
            value = 5, createdAt = "c", updatedAt = "u", acknowledged = true, occurrenceCount = 3
        )
        assertThat(alarm.value).isEqualTo(5)
        assertThat(alarm.occurrenceCount).isEqualTo(3)
        assertThat(alarm.acknowledged).isTrue()
    }

    @Test
    fun `AlarmInfo equals distinguishes on acknowledged, cause, value and occurrenceCount`() {
        val base = CarelevoAlarmInfoEntity(
            "a", AlarmType.ALERT.code, AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            value = null, createdAt = "c", updatedAt = "u", acknowledged = false
        )
        assertThat(base.copy(acknowledged = true)).isNotEqualTo(base)
        assertThat(base.copy(cause = AlarmCause.ALARM_ALERT_LOW_BATTERY)).isNotEqualTo(base)
        assertThat(base.copy(value = 1)).isNotEqualTo(base)
        assertThat(base.copy(occurrenceCount = 2)).isNotEqualTo(base)
        assertThat(base.copy()).isEqualTo(base)
        assertThat(base.copy().hashCode()).isEqualTo(base.hashCode())
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoPatchInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `PatchInfo minimal constructor defaults every optional to null`() {
        val patch = CarelevoPatchInfoEntity(address = "AA:BB", createdAt = "c", updatedAt = "u")
        assertThat(patch.address).isEqualTo("AA:BB")
        assertThat(patch.createdAt).isEqualTo("c")
        assertThat(patch.updatedAt).isEqualTo("u")
        assertThat(patch.manufactureNumber).isNull()
        assertThat(patch.firmwareVersion).isNull()
        assertThat(patch.bootDateTime).isNull()
        assertThat(patch.bootDateTimeUtcMillis).isNull()
        assertThat(patch.modelName).isNull()
        assertThat(patch.insulinAmount).isNull()
        assertThat(patch.insulinRemain).isNull()
        assertThat(patch.thresholdInsulinRemain).isNull()
        assertThat(patch.thresholdExpiry).isNull()
        assertThat(patch.thresholdMaxBasalSpeed).isNull()
        assertThat(patch.thresholdMaxBolusDose).isNull()
        assertThat(patch.checkSafety).isNull()
        assertThat(patch.checkNeedle).isNull()
        assertThat(patch.needleFailedCount).isNull()
        assertThat(patch.isConnected).isNull()
        assertThat(patch.needDiscard).isNull()
        assertThat(patch.isDiscard).isNull()
        assertThat(patch.isExtended).isNull()
        assertThat(patch.isValid).isNull()
        assertThat(patch.isStopped).isNull()
        assertThat(patch.stopMinutes).isNull()
        assertThat(patch.stopMode).isNull()
        assertThat(patch.isForceStopped).isNull()
        assertThat(patch.runningMinutes).isNull()
        assertThat(patch.infusedTotalBasalAmount).isNull()
        assertThat(patch.infusedTotalBolusAmount).isNull()
        assertThat(patch.pumpState).isNull()
        assertThat(patch.mode).isNull()
        assertThat(patch.bolusActionSeq).isNull()
    }

    @Test
    fun `PatchInfo fully-populated retains representative fields`() {
        val patch = CarelevoPatchInfoEntity(
            address = "AA:BB:CC:DD",
            createdAt = "2026-07-16T00:00:00",
            updatedAt = "2026-07-16T01:00:00",
            manufactureNumber = "MN-123",
            firmwareVersion = "1.2.3",
            bootDateTime = "2026-07-15T12:00:00",
            bootDateTimeUtcMillis = 1_752_580_800_000L,
            modelName = "Carelevo",
            insulinAmount = 200,
            insulinRemain = 123.4,
            thresholdInsulinRemain = 20,
            thresholdExpiry = 116,
            thresholdMaxBasalSpeed = 2.5,
            thresholdMaxBolusDose = 15.0,
            checkSafety = true,
            checkNeedle = false,
            needleFailedCount = 2,
            isConnected = true,
            needDiscard = false,
            isDiscard = false,
            isExtended = true,
            isValid = true,
            isStopped = false,
            stopMinutes = 0,
            stopMode = 1,
            isForceStopped = false,
            runningMinutes = 42,
            infusedTotalBasalAmount = 10.5,
            infusedTotalBolusAmount = 3.25,
            pumpState = 4,
            mode = 2,
            bolusActionSeq = 7
        )
        assertThat(patch.manufactureNumber).isEqualTo("MN-123")
        assertThat(patch.firmwareVersion).isEqualTo("1.2.3")
        assertThat(patch.bootDateTimeUtcMillis).isEqualTo(1_752_580_800_000L)
        assertThat(patch.insulinRemain).isEqualTo(123.4)
        assertThat(patch.thresholdMaxBasalSpeed).isEqualTo(2.5)
        assertThat(patch.checkSafety).isTrue()
        assertThat(patch.checkNeedle).isFalse()
        assertThat(patch.isExtended).isTrue()
        assertThat(patch.runningMinutes).isEqualTo(42)
        assertThat(patch.infusedTotalBasalAmount).isEqualTo(10.5)
        assertThat(patch.pumpState).isEqualTo(4)
        assertThat(patch.mode).isEqualTo(2)
        assertThat(patch.bolusActionSeq).isEqualTo(7)
    }

    @Test
    fun `PatchInfo copy alters one field, others survive, equality restores`() {
        val base = CarelevoPatchInfoEntity(address = "AA:BB", createdAt = "c", updatedAt = "u")
        val updated = base.copy(insulinRemain = 50.0, isConnected = true)
        assertThat(updated).isNotEqualTo(base)
        assertThat(updated.insulinRemain).isEqualTo(50.0)
        assertThat(updated.isConnected).isTrue()
        assertThat(updated.address).isEqualTo("AA:BB")
        // reverting the changed fields restores full equality + hashCode.
        val reverted = updated.copy(insulinRemain = null, isConnected = null)
        assertThat(reverted).isEqualTo(base)
        assertThat(reverted.hashCode()).isEqualTo(base.hashCode())
    }

    @Test
    fun `PatchInfo equals distinguishes on nullable boolean and numeric fields`() {
        val base = CarelevoPatchInfoEntity(address = "AA:BB", createdAt = "c", updatedAt = "u")
        assertThat(base.copy(isValid = false)).isNotEqualTo(base)
        assertThat(base.copy(isStopped = true)).isNotEqualTo(base)
        assertThat(base.copy(isForceStopped = true)).isNotEqualTo(base)
        assertThat(base.copy(needDiscard = true)).isNotEqualTo(base)
        assertThat(base.copy(pumpState = 1)).isNotEqualTo(base)
        assertThat(base.copy(address = "ZZ:ZZ")).isNotEqualTo(base)
    }

    @Test
    fun `PatchInfo first three destructured components are the required fields`() {
        val patch = CarelevoPatchInfoEntity(address = "AA:BB", createdAt = "c", updatedAt = "u")
        assertThat(patch.component1()).isEqualTo("AA:BB")
        assertThat(patch.component2()).isEqualTo("c")
        assertThat(patch.component3()).isEqualTo("u")
        assertThat(patch.toString()).contains("address=AA:BB")
    }

    // ---------------------------------------------------------------------------------------------
    // CarelevoUserSettingInfoEntity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `UserSetting minimal constructor defaults optionals to null and sync flags to false`() {
        val setting = CarelevoUserSettingInfoEntity(createdAt = "c", updatedAt = "u")
        assertThat(setting.createdAt).isEqualTo("c")
        assertThat(setting.updatedAt).isEqualTo("u")
        assertThat(setting.lowInsulinNoticeAmount).isNull()
        assertThat(setting.maxBasalSpeed).isNull()
        assertThat(setting.maxBolusDose).isNull()
        assertThat(setting.needLowInsulinNoticeAmountSyncPatch).isFalse()
        assertThat(setting.needMaxBasalSpeedSyncPatch).isFalse()
        assertThat(setting.needMaxBolusDoseSyncPatch).isFalse()
    }

    @Test
    fun `UserSetting fully-populated retains all values`() {
        val setting = CarelevoUserSettingInfoEntity(
            createdAt = "c",
            updatedAt = "u",
            lowInsulinNoticeAmount = 20,
            maxBasalSpeed = 2.5,
            maxBolusDose = 15.0,
            needLowInsulinNoticeAmountSyncPatch = true,
            needMaxBasalSpeedSyncPatch = true,
            needMaxBolusDoseSyncPatch = true
        )
        assertThat(setting.lowInsulinNoticeAmount).isEqualTo(20)
        assertThat(setting.maxBasalSpeed).isEqualTo(2.5)
        assertThat(setting.maxBolusDose).isEqualTo(15.0)
        assertThat(setting.needLowInsulinNoticeAmountSyncPatch).isTrue()
        assertThat(setting.needMaxBasalSpeedSyncPatch).isTrue()
        assertThat(setting.needMaxBolusDoseSyncPatch).isTrue()
    }

    @Test
    fun `UserSetting equals distinguishes on each sync flag and value`() {
        val base = CarelevoUserSettingInfoEntity(createdAt = "c", updatedAt = "u")
        assertThat(base.copy(needLowInsulinNoticeAmountSyncPatch = true)).isNotEqualTo(base)
        assertThat(base.copy(needMaxBasalSpeedSyncPatch = true)).isNotEqualTo(base)
        assertThat(base.copy(needMaxBolusDoseSyncPatch = true)).isNotEqualTo(base)
        assertThat(base.copy(lowInsulinNoticeAmount = 10)).isNotEqualTo(base)
        assertThat(base.copy(maxBasalSpeed = 1.0)).isNotEqualTo(base)
        assertThat(base.copy(maxBolusDose = 5.0)).isNotEqualTo(base)
        assertThat(base.copy()).isEqualTo(base)
        assertThat(base.copy().hashCode()).isEqualTo(base.hashCode())
    }

    @Test
    fun `UserSetting destructures in declaration order`() {
        val setting = CarelevoUserSettingInfoEntity(
            "c", "u", 20, 2.5, 15.0, true, false, true
        )
        val (createdAt, updatedAt, low, maxBasal, maxBolus, syncLow, syncBasal, syncBolus) = setting
        assertThat(createdAt).isEqualTo("c")
        assertThat(updatedAt).isEqualTo("u")
        assertThat(low).isEqualTo(20)
        assertThat(maxBasal).isEqualTo(2.5)
        assertThat(maxBolus).isEqualTo(15.0)
        assertThat(syncLow).isTrue()
        assertThat(syncBasal).isFalse()
        assertThat(syncBolus).isTrue()
    }
}
