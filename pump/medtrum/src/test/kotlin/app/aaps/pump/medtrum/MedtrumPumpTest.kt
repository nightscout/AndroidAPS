package app.aaps.pump.medtrum

import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.ModelType
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MedtrumPumpTest : MedtrumTestBase() {

    @Test fun buildMedtrumProfileArrayGivenProfileWhenValuesSetThenReturnCorrectByteArray() {
        // Inputs
        // Basal profile with 7 elements:
        // 00:00 : 2.1
        // 04:00 : 1.9
        // 06:00 : 1.7
        // 08:00 : 1.5
        // 16:00 : 1.6
        // 21:00 : 1.7
        // 23:00 : 2
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"2.1\"},{\"time\":\"04:00\",\"value\":\"1.9\"},{\"time\":\"06:00\",\"value\":\"1.7\"}," +
            "{\"time\":\"08:00\",\"value\":\"1.5\"},{\"time\":\"16:00\",\"value\":\"1.6\"},{\"time\":\"21:00\",\"value\":\"1.7\"},{\"time\":\"23:00\",\"value\":\"2\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!, activePlugin)

        // Call
        val result = medtrumPump.buildMedtrumProfileArray(profile)

        // Expected values
        val expectedByteArray = byteArrayOf(7, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -20, 36, 2, 100, -123, 2)
        assertThat(result!!.contentToString()).isEqualTo(expectedByteArray.contentToString())
    }

    @Test fun buildMedtrumProfileArrayGiveProfileWhenValuesTooHighThenReturnNull() {
        // Inputs
        // Basal profile with 1 element:
        // 00:00 : 600
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"600\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!, activePlugin)

        // Call
        val result = medtrumPump.buildMedtrumProfileArray(profile)

        // Expected values
        assertThat(result).isNull()
    }

    @Test fun getCurrentHourlyBasalFromMedtrumProfileArrayGivenProfileWhenValuesSetThenReturnCorrectValue() {
        // Inputs
        // Basal profile with 7 elements:
        // 00:00 : 2.1
        // 04:00 : 1.9
        // 06:00 : 1.7
        // 08:00 : 1.5
        // 16:00 : 1.6
        // 21:00 : 1.7
        // 23:00 : 2
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"2.1\"},{\"time\":\"04:00\",\"value\":\"1.9\"},{\"time\":\"06:00\",\"value\":\"1.7\"}," +
            "{\"time\":\"08:00\",\"value\":\"1.5\"},{\"time\":\"16:00\",\"value\":\"1.6\"},{\"time\":\"21:00\",\"value\":\"1.7\"},{\"time\":\"23:00\",\"value\":\"2\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!, activePlugin)
        val profileArray = medtrumPump.buildMedtrumProfileArray(profile)

        val localDate = LocalDate.of(2023, 1, 1)

        // For 03:59
        val localTime0399 = LocalTime.of(3, 59)
        val zonedDateTime0399 = localDate.atTime(localTime0399).atZone(ZoneId.systemDefault())
        val time0399 = zonedDateTime0399.toInstant().toEpochMilli()
        val result = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray!!, time0399)
        assertThat(result).isWithin(0.01).of(2.1)

        // For 22:30
        val localTime2230 = LocalTime.of(22, 30)
        val zonedDateTime2230 = localDate.atTime(localTime2230).atZone(ZoneId.systemDefault())
        val time2230 = zonedDateTime2230.toInstant().toEpochMilli()
        val result1 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray, time2230)
        assertThat(result1).isWithin(0.01).of(1.7)

        // For 23:59
        val localTime2359 = LocalTime.of(23, 59)
        val zonedDateTime2359 = localDate.atTime(localTime2359).atZone(ZoneId.systemDefault())
        val time2359 = zonedDateTime2359.toInstant().toEpochMilli()
        val result2 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray, time2359)
        assertThat(result2).isWithin(0.01).of(2.0)

        // For 00:00
        val localTime0000 = LocalTime.of(0, 0)
        val zonedDateTime0000 = localDate.atTime(localTime0000).atZone(ZoneId.systemDefault())
        val time0000 = zonedDateTime0000.toInstant().toEpochMilli()
        val result3 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray, time0000)
        assertThat(result3).isWithin(0.01).of(2.1)
    }

    @Test fun handleBolusStatusUpdateWhenCalledExpectNewData() {
        // Inputs
        val bolusType = 0
        val bolusCompleted = false
        val amount = 1.4

        // Call
        medtrumPump.handleBolusStatusUpdate(bolusType, bolusCompleted, amount)

        // Expected values
        assertThat(medtrumPump.bolusDone).isEqualTo(bolusCompleted)
        assertThat(medtrumPump.bolusAmountDeliveredFlow.value).isWithin(0.01).of(amount)
        assertThat(BolusProgressData.delivered).isWithin(0.01).of(amount)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsAbsoluteTempAndTemporaryBasalInfoThenExpectNewData() {
        // Inputs
        val basalType = BasalType.ABSOLUTE_TEMP
        val basalRate = 0.5
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L
        val duration = T.mins(5).msecs()

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        val temporaryBasalInfo: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(temporaryBasalInfo.duration).thenReturn(duration)

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        whenever(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(temporaryBasalInfo)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = PumpRate(basalRate),
            duration = duration,
            isAbsolute = true,
            type = temporaryBasalInfo.type,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsAbsoluteTempAndSameExpectedTemporaryBasalInfoThenExpectNoPumpSync() {
        // Inputs
        val basalType = BasalType.ABSOLUTE_TEMP
        val basalRate = 0.5
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime) // Ensure it's the same as input startTime

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )

        // Check that other fields in medtrumPump are updated
        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsAbsoluteTempAndNoTemporaryBasalInfoThenExpectNewData() {
        // Inputs
        val basalType = BasalType.ABSOLUTE_TEMP
        val basalRate = 0.5
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        val temporaryBasalInfo: PumpSync.PumpState.TemporaryBasal? = null

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        whenever(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(temporaryBasalInfo)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = PumpRate(basalRate),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = null,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsRelativeTempAndTemporaryBasalInfoThenExpectNewData() {
        // Inputs
        val basalType = BasalType.RELATIVE_TEMP
        val basalRate = 0.5
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L
        val duration = T.mins(5).msecs()

        medtrumPump.actualBasalProfile = medtrumPump.buildMedtrumProfileArray(validProfile)!!
        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        val temporaryBasalInfo: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(temporaryBasalInfo.duration).thenReturn(duration)

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        whenever(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(temporaryBasalInfo)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        val adjustedBasalRate = (basalRate / medtrumPump.baseBasalRate) * 100
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = PumpRate(adjustedBasalRate),
            duration = duration,
            isAbsolute = false,
            type = temporaryBasalInfo.type,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsSuspendedThenExpectNewData() {
        // Inputs
        val basalType = BasalType.SUSPEND_MORE_THAN_MAX_PER_DAY
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        whenever(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(null)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = PumpRate(basalRate),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsATypeIsSuspendedAndSameExpectedTemporaryBasalInfoThenExpectNoPumpSync() {
        // Inputs
        val basalType = BasalType.SUSPEND_MORE_THAN_MAX_PER_DAY
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime) // Ensure it's the same as input startTime

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsSuspendedAndNewerFakeTBRThenExpectInvalidateAndNewData() {
        // Inputs
        val basalType = BasalType.SUSPEND_MORE_THAN_MAX_PER_DAY
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime + T.mins(10).msecs()) // Ensure it's different
        whenever(expectedTemporaryBasal.timestamp).thenReturn(basalStartTime + T.mins(10).msecs())  // Newer Fake TBR
        whenever(expectedTemporaryBasal.duration).thenReturn(T.mins(4800L).msecs()) // Fake TBR duration

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        whenever(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(null)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = PumpRate(basalRate),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsNoneAndThenExpectFakeTBR() {
        // Inputs
        val basalType = BasalType.NONE
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = null,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = PumpRate(basalRate),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = dateUtil.now(),
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsStandardAndTempBasalExpectedThenExpectSyncStop() {
        // Inputs
        val basalType = BasalType.STANDARD
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync).syncStopTemporaryBasalWithPumpId(
            timestamp = basalStartTime + 250,
            endPumpId = basalStartTime + 250,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsStandardAndNoTempBasalExpectedThenExpectNoSyncStop() {
        // Inputs
        val basalType = BasalType.STANDARD
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = null,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )

        assertThat(medtrumPump.lastBasalType).isEqualTo(basalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(basalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(basalSequence)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(basalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(basalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(basalStartTime)
    }

    @Test fun handleNewPatchCalledWhenCalledExpectNewDataPresent() {
        // Inputs
        medtrumPump.currentSequenceNumber = 100
        medtrumPump.syncedSequenceNumber = 100

        val newPatchId = 3L
        val newSequenceNumber = 1
        val newStartTime = 1000L

        // Call
        medtrumPump.handleNewPatch(newPatchId, newSequenceNumber, newStartTime)

        // Expected values
        assertThat(medtrumPump.patchId).isEqualTo(newPatchId)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(newSequenceNumber)
        assertThat(medtrumPump.patchStartTime).isEqualTo(newStartTime)
        assertThat(medtrumPump.syncedSequenceNumber).isEqualTo(1)
    }

    @Test fun handleStopStatusUpdateWhenSequenceThenExpectUpdate() {
        // Inputs
        medtrumPump.currentSequenceNumber = 100
        medtrumPump.syncedSequenceNumber = 100

        val sequence = 101
        val patchId = 3L

        // Call
        medtrumPump.handleStopStatusUpdate(sequence, patchId)

        // Expected values
        assertThat(medtrumPump.lastStopPatchId).isEqualTo(patchId)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(sequence)
    }

    @Test fun setFakeTBRIfNotSetWhenNoFakeTBRAlreadyRunningExpectPumpSync() {
        // Inputs
        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.duration).thenReturn(T.mins(30L).msecs())

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.setFakeTBRIfNotSet()

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = PumpRate(0.0),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = dateUtil.now(),
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )
    }

    @Test fun setFakeTBRIfNotSetWhenFakeTBRAlreadyRunningExpectNoPumpSync() {
        // Inputs
        medtrumPump.deviceType = ModelType.MD8301.value

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.duration).thenReturn(T.mins(4800L).msecs())

        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )

        // Call
        medtrumPump.setFakeTBRIfNotSet()

        // Expected values
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test fun handleNewPatchCalledWhenSequenceNumberThenExpectPumpSyncCalled() {
        // Inputs
        medtrumPump.currentSequenceNumber = 100
        medtrumPump.syncedSequenceNumber = 99

        val newPatchId = 3L
        val newSequenceNumber = 1
        val newStartTime = 1000L

        // Call
        medtrumPump.handleNewPatch(newPatchId, newSequenceNumber, newStartTime)

        // Expected values
        verify(pumpSync, times(1)).insertTherapyEventIfNewWithTimestamp(
            newStartTime,
            TE.Type.CANNULA_CHANGE,
            null,
            null,
            medtrumPump.pumpType(),
            medtrumPump.pumpSN.toString(radix = 16)
        )

        verify(pumpSync, times(1)).insertTherapyEventIfNewWithTimestamp(
            newStartTime,
            TE.Type.INSULIN_CHANGE,
            null,
            null,
            medtrumPump.pumpType(),
            medtrumPump.pumpSN.toString(radix = 16)
        )
    }
}
