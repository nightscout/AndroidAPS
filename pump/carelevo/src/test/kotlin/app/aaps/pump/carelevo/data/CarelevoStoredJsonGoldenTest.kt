package app.aaps.pump.carelevo.data

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.data.dao.CarelevoAlarmInfoDaoImpl
import app.aaps.pump.carelevo.data.dao.CarelevoInfusionInfoDaoImpl
import app.aaps.pump.carelevo.data.dao.CarelevoPatchInfoDaoImpl
import app.aaps.pump.carelevo.data.dao.CarelevoUserSettingInfoDaoImpl
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalSegmentInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * What the driver stores, byte for byte. Every JSON string here was written by the Gson code of 2026-11-17, so these
 * tests prove that a new store format reads what users already have on their phones, and writes what an older build
 * can still read after a downgrade.
 *
 * Each test goes through a DAO only, never through the JSON library, so it holds for any library behind the DAOs.
 */
internal class CarelevoStoredJsonGoldenTest {

    private val store = mutableMapOf<String, String>()
    private val preferences: Preferences = mock()

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenAnswer {
            val key = it.getArgument<StringNonPreferenceKey>(0)
            store[key.key] ?: key.defaultValue
        }
        doAnswer {
            store[it.getArgument<StringNonPreferenceKey>(0).key] = it.getArgument<String>(1)
            null
        }.whenever(preferences).put(any<StringNonPreferenceKey>(), any<String>())
    }

    // ---- patch info -----------------------------------------------------------------------------

    @Test
    fun `a full patch info is written as before`() {
        CarelevoPatchInfoDaoImpl(preferences).updatePatchInfo(PATCH_FULL)

        assertThat(store[CarelevoStringNonKey.PatchInfo.key]).isEqualTo(PATCH_FULL_JSON)
    }

    @Test
    fun `a stored full patch info is read as before`() {
        store[CarelevoStringNonKey.PatchInfo.key] = PATCH_FULL_JSON

        assertThat(CarelevoPatchInfoDaoImpl(preferences).getPatchInfoBySync()).isEqualTo(PATCH_FULL)
    }

    @Test
    fun `a patch info with only the required fields leaves the others out`() {
        CarelevoPatchInfoDaoImpl(preferences).updatePatchInfo(PATCH_MINIMAL)

        assertThat(store[CarelevoStringNonKey.PatchInfo.key]).isEqualTo(PATCH_MINIMAL_JSON)
        assertThat(CarelevoPatchInfoDaoImpl(preferences).getPatchInfoBySync()).isEqualTo(PATCH_MINIMAL)
    }

    @Test
    fun `an insulin amount that is not a number is kept`() {
        CarelevoPatchInfoDaoImpl(preferences).updatePatchInfo(PATCH_NAN)

        assertThat(store[CarelevoStringNonKey.PatchInfo.key]).isEqualTo(PATCH_NAN_JSON)
        assertThat(CarelevoPatchInfoDaoImpl(preferences).getPatchInfoBySync()!!.insulinRemain!!.isNaN()).isTrue()
    }

    @Test
    fun `a field this build does not know is skipped`() {
        store[CarelevoStringNonKey.PatchInfo.key] = PATCH_MINIMAL_JSON.dropLast(1) + ""","removedField":42,"removedObject":{"a":[1,2]}}"""

        assertThat(CarelevoPatchInfoDaoImpl(preferences).getPatchInfoBySync()).isEqualTo(PATCH_MINIMAL)
    }

    // ---- infusion info --------------------------------------------------------------------------

    @Test
    fun `the four infusion records are written as before`() {
        val dao = CarelevoInfusionInfoDaoImpl(preferences)

        dao.updateBasalInfusionInfo(BASAL)
        dao.updateTempBasalInfusionInfo(TEMP_PERCENT)
        dao.updateImmeBolusInfusionInfo(IMME)
        dao.updateExtendBolusInfusionInfo(EXTEND)

        assertThat(store[CarelevoStringNonKey.BasalInfusionInfo.key]).isEqualTo(BASAL_JSON)
        assertThat(store[CarelevoStringNonKey.TempBasalInfusionInfo.key]).isEqualTo(TEMP_PERCENT_JSON)
        assertThat(store[CarelevoStringNonKey.ImmeBolusInfusionInfo.key]).isEqualTo(IMME_JSON)
        assertThat(store[CarelevoStringNonKey.ExtendBolusInfusionInfo.key]).isEqualTo(EXTEND_JSON)
    }

    @Test
    fun `the four stored infusion records are read as before`() {
        store[CarelevoStringNonKey.BasalInfusionInfo.key] = BASAL_JSON
        store[CarelevoStringNonKey.TempBasalInfusionInfo.key] = TEMP_SPEED_JSON
        store[CarelevoStringNonKey.ImmeBolusInfusionInfo.key] = IMME_JSON
        store[CarelevoStringNonKey.ExtendBolusInfusionInfo.key] = EXTEND_JSON

        val info = CarelevoInfusionInfoDaoImpl(preferences).getInfusionInfoBySync()!!

        assertThat(info.basalInfusionInfo).isEqualTo(BASAL)
        assertThat(info.tempBasalInfusionInfo).isEqualTo(TEMP_SPEED)
        assertThat(info.immeBolusInfusionInfo).isEqualTo(IMME)
        assertThat(info.extendBolusInfusionInfo).isEqualTo(EXTEND)
    }

    @Test
    fun `a temp basal by speed and a bolus with only the required fields are written as before`() {
        val dao = CarelevoInfusionInfoDaoImpl(preferences)

        dao.updateTempBasalInfusionInfo(TEMP_SPEED)
        dao.updateImmeBolusInfusionInfo(IMME_MINIMAL)

        assertThat(store[CarelevoStringNonKey.TempBasalInfusionInfo.key]).isEqualTo(TEMP_SPEED_JSON)
        assertThat(store[CarelevoStringNonKey.ImmeBolusInfusionInfo.key]).isEqualTo(IMME_MINIMAL_JSON)
        assertThat(CarelevoInfusionInfoDaoImpl(preferences).getInfusionInfoBySync()!!.immeBolusInfusionInfo).isEqualTo(IMME_MINIMAL)
    }

    // ---- user settings --------------------------------------------------------------------------

    @Test
    fun `the user settings are written and read as before`() {
        CarelevoUserSettingInfoDaoImpl(preferences).updateUserSetting(SETTING)

        assertThat(store[CarelevoStringNonKey.UserSettingInfo.key]).isEqualTo(SETTING_JSON)
        assertThat(CarelevoUserSettingInfoDaoImpl(preferences).getUserSettingBySync()).isEqualTo(SETTING)
    }

    @Test
    fun `user settings with no values still write their three flags`() {
        CarelevoUserSettingInfoDaoImpl(preferences).updateUserSetting(SETTING_MINIMAL)

        assertThat(store[CarelevoStringNonKey.UserSettingInfo.key]).isEqualTo(SETTING_MINIMAL_JSON)
        assertThat(CarelevoUserSettingInfoDaoImpl(preferences).getUserSettingBySync()).isEqualTo(SETTING_MINIMAL)
    }

    // ---- alarms ---------------------------------------------------------------------------------

    @Test
    fun `the alarm list is written as before`() {
        CarelevoAlarmInfoDaoImpl(preferences).setAlarms(ALARMS)

        assertThat(store[CarelevoStringNonKey.AlarmInfoList.key]).isEqualTo(ALARMS_JSON)
    }

    @Test
    fun `a stored alarm list is read as before`() {
        store[CarelevoStringNonKey.AlarmInfoList.key] = ALARMS_JSON

        assertThat(CarelevoAlarmInfoDaoImpl(preferences).getAlarmsOnce()).isEqualTo(ALARMS)
    }

    private companion object {

        const val C = "2026-11-17T08:00:00Z"
        const val U = "2026-11-17T09:30:00Z"
        const val A = "aa:bb:cc:dd:ee:ff"

        val PATCH_FULL = CarelevoPatchInfoEntity(
            address = A, createdAt = C, updatedAt = U, manufactureNumber = "CL2411170001", firmwareVersion = "1.2.3",
            bootDateTime = "241117080000", bootDateTimeUtcMillis = 1795075200000L, modelName = "CARELEVO", insulinAmount = 200,
            insulinRemain = 187.35, thresholdInsulinRemain = 20, thresholdExpiry = 116, thresholdMaxBasalSpeed = 15.0,
            thresholdMaxBolusDose = 10.5, checkSafety = true, checkNeedle = true, needleFailedCount = 0, isConnected = false,
            needDiscard = false, isDiscard = false, isExtended = false, isValid = true, isStopped = true, stopMinutes = 30,
            stopMode = 1, isForceStopped = false, runningMinutes = 95, infusedTotalBasalAmount = 1.25,
            infusedTotalBolusAmount = 11.4, pumpState = 2, mode = 1, bolusActionSeq = 7
        )
        const val PATCH_FULL_JSON =
            """{"address":"aa:bb:cc:dd:ee:ff","createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","manufactureNumber":"CL2411170001","firmwareVersion":"1.2.3","bootDateTime":"241117080000","bootDateTimeUtcMillis":1795075200000,"modelName":"CARELEVO","insulinAmount":200,"insulinRemain":187.35,"thresholdInsulinRemain":20,"thresholdExpiry":116,"thresholdMaxBasalSpeed":15.0,"thresholdMaxBolusDose":10.5,"checkSafety":true,"checkNeedle":true,"needleFailedCount":0,"isConnected":false,"needDiscard":false,"isDiscard":false,"isExtended":false,"isValid":true,"isStopped":true,"stopMinutes":30,"stopMode":1,"isForceStopped":false,"runningMinutes":95,"infusedTotalBasalAmount":1.25,"infusedTotalBolusAmount":11.4,"pumpState":2,"mode":1,"bolusActionSeq":7}"""

        val PATCH_MINIMAL = CarelevoPatchInfoEntity(address = A, createdAt = C, updatedAt = U)
        const val PATCH_MINIMAL_JSON = """{"address":"aa:bb:cc:dd:ee:ff","createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z"}"""

        val PATCH_NAN = CarelevoPatchInfoEntity(address = A, createdAt = C, updatedAt = U, insulinRemain = Double.NaN)
        const val PATCH_NAN_JSON = """{"address":"aa:bb:cc:dd:ee:ff","createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","insulinRemain":NaN}"""

        val BASAL = CarelevoBasalInfusionInfoEntity(
            infusionId = "basal-1", address = A, mode = 1, createdAt = C, updatedAt = U,
            segments = listOf(
                CarelevoBasalSegmentInfusionInfoEntity(createdAt = C, updatedAt = U, startTime = 0, endTime = 60, speed = 1.0),
                CarelevoBasalSegmentInfusionInfoEntity(createdAt = C, updatedAt = U, startTime = 60, endTime = 1440, speed = 0.85)
            ),
            isStop = false
        )
        const val BASAL_JSON =
            """{"infusionId":"basal-1","address":"aa:bb:cc:dd:ee:ff","mode":1,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","segments":[{"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","startTime":0,"endTime":60,"speed":1.0},{"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","startTime":60,"endTime":1440,"speed":0.85}],"isStop":false}"""

        val TEMP_PERCENT = CarelevoTempBasalInfusionInfoEntity(infusionId = "temp-1", address = A, mode = 2, createdAt = C, updatedAt = U, percent = 150, infusionDurationMin = 60)
        const val TEMP_PERCENT_JSON =
            """{"infusionId":"temp-1","address":"aa:bb:cc:dd:ee:ff","mode":2,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","percent":150,"infusionDurationMin":60}"""

        val TEMP_SPEED = CarelevoTempBasalInfusionInfoEntity(infusionId = "temp-2", address = A, mode = 2, createdAt = C, updatedAt = U, speed = 1.5, infusionDurationMin = 30)
        const val TEMP_SPEED_JSON =
            """{"infusionId":"temp-2","address":"aa:bb:cc:dd:ee:ff","mode":2,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","speed":1.5,"infusionDurationMin":30}"""

        val IMME = CarelevoImmeBolusInfusionInfoEntity(
            infusionId = "imme-1", address = A, mode = 3, createdAt = C, updatedAt = U, volume = 2.5, infusionDurationSeconds = 75,
            pumpId = 123456789012L, bolusTotalBefore = 40.2, bolusType = "SMB"
        )
        const val IMME_JSON =
            """{"infusionId":"imme-1","address":"aa:bb:cc:dd:ee:ff","mode":3,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","volume":2.5,"infusionDurationSeconds":75,"pumpId":123456789012,"bolusTotalBefore":40.2,"bolusType":"SMB"}"""

        val IMME_MINIMAL = CarelevoImmeBolusInfusionInfoEntity(infusionId = "imme-2", address = A, mode = 3, createdAt = C, updatedAt = U)
        const val IMME_MINIMAL_JSON =
            """{"infusionId":"imme-2","address":"aa:bb:cc:dd:ee:ff","mode":3,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z"}"""

        val EXTEND = CarelevoExtendBolusInfusionInfoEntity(infusionId = "ext-1", address = A, mode = 4, createdAt = C, updatedAt = U, volume = 3.0, speed = 1.5, infusionDurationMin = 120)
        const val EXTEND_JSON =
            """{"infusionId":"ext-1","address":"aa:bb:cc:dd:ee:ff","mode":4,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","volume":3.0,"speed":1.5,"infusionDurationMin":120}"""

        val SETTING = CarelevoUserSettingInfoEntity(
            createdAt = C, updatedAt = U, lowInsulinNoticeAmount = 20, maxBasalSpeed = 15.0, maxBolusDose = 10.0,
            needLowInsulinNoticeAmountSyncPatch = true, needMaxBasalSpeedSyncPatch = false, needMaxBolusDoseSyncPatch = true
        )
        const val SETTING_JSON =
            """{"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","lowInsulinNoticeAmount":20,"maxBasalSpeed":15.0,"maxBolusDose":10.0,"needLowInsulinNoticeAmountSyncPatch":true,"needMaxBasalSpeedSyncPatch":false,"needMaxBolusDoseSyncPatch":true}"""

        val SETTING_MINIMAL = CarelevoUserSettingInfoEntity(createdAt = C, updatedAt = U)
        const val SETTING_MINIMAL_JSON =
            """{"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","needLowInsulinNoticeAmountSyncPatch":false,"needMaxBasalSpeedSyncPatch":false,"needMaxBolusDoseSyncPatch":false}"""

        val ALARMS = listOf(
            CarelevoAlarmInfoEntity(
                alarmId = "alarm-1", alarmType = AlarmCause.ALARM_WARNING_LOW_INSULIN.alarmType.code, cause = AlarmCause.ALARM_WARNING_LOW_INSULIN,
                value = 30, createdAt = C, updatedAt = U, acknowledged = false, occurrenceCount = 3
            ),
            CarelevoAlarmInfoEntity(
                alarmId = "alarm-2", alarmType = AlarmCause.ALARM_WARNING_PUMP_CLOGGED.alarmType.code, cause = AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
                createdAt = C, updatedAt = U, acknowledged = false
            )
        )
        const val ALARMS_JSON =
            """[{"alarmId":"alarm-1","alarmType":0,"cause":"ALARM_WARNING_LOW_INSULIN","value":30,"createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","acknowledged":false,"occurrenceCount":3},{"alarmId":"alarm-2","alarmType":0,"cause":"ALARM_WARNING_PUMP_CLOGGED","createdAt":"2026-11-17T08:00:00Z","updatedAt":"2026-11-17T09:30:00Z","acknowledged":false,"occurrenceCount":1}]"""
    }
}
