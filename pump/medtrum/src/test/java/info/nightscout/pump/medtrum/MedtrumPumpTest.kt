package info.nightscout.pump.medtrum

import app.aaps.core.main.extensions.pureProfileFromJson
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.utils.T
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.util.MedtrumSnUtil
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
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
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!)

        // Call
        val result = medtrumPump.buildMedtrumProfileArray(profile)

        // Expected values
        val expectedByteArray = byteArrayOf(7, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -20, 36, 2, 100, -123, 2)
        Assertions.assertEquals(expectedByteArray.contentToString(), result?.contentToString())
    }

    @Test fun buildMedtrumProfileArrayGiveProfileWhenValuesTooHighThenReturnNull() {
        // Inputs
        // Basal profile with 1 element:
        // 00:00 : 600
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"600\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!)

        // Call
        val result = medtrumPump.buildMedtrumProfileArray(profile)

        // Expected values
        Assertions.assertNull(result)
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
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!)
        val profileArray = medtrumPump.buildMedtrumProfileArray(profile)

        val localDate = LocalDate.of(2023, 1, 1)

        // For 03:59
        val localTime0399 = LocalTime.of(3, 59)
        val zonedDateTime0399 = localDate.atTime(localTime0399).atZone(ZoneId.systemDefault())
        val time0399 = zonedDateTime0399.toInstant().toEpochMilli()
        val result = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray!!, time0399)
        Assertions.assertEquals(2.1, result, 0.01)

        // For 22:30
        val localTime2230 = LocalTime.of(22, 30)
        val zonedDateTime2230 = localDate.atTime(localTime2230).atZone(ZoneId.systemDefault())
        val time2230 = zonedDateTime2230.toInstant().toEpochMilli()
        val result1 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray, time2230)
        Assertions.assertEquals(1.7, result1, 0.01)

        // For 23:59
        val localTime2359 = LocalTime.of(23, 59)
        val zonedDateTime2359 = localDate.atTime(localTime2359).atZone(ZoneId.systemDefault())
        val time2359 = zonedDateTime2359.toInstant().toEpochMilli()
        val result2 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray, time2359)
        Assertions.assertEquals(2.0, result2, 0.01)

        // For 00:00
        val localTime0000 = LocalTime.of(0, 0)
        val zonedDateTime0000 = localDate.atTime(localTime0000).atZone(ZoneId.systemDefault())
        val time0000 = zonedDateTime0000.toInstant().toEpochMilli()
        val result3 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray, time0000)
        Assertions.assertEquals(2.1, result3, 0.01)
    }

    @Test fun handleBolusStatusUpdateWhenCalledExpectNewData() {
        // Inputs
        val bolusType = 0
        val bolusCompleted = false
        val amount = 1.4

        medtrumPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)

        // Call
        medtrumPump.handleBolusStatusUpdate(bolusType, bolusCompleted, amount)

        // Expected values
        Assertions.assertEquals(bolusCompleted, medtrumPump.bolusDone)
        Assertions.assertEquals(amount, medtrumPump.bolusAmountDeliveredFlow.value, 0.01)
        Assertions.assertEquals(amount, medtrumPump.bolusingTreatment!!.insulin, 0.01)
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

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        val temporaryBasalInfo: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(temporaryBasalInfo.duration).thenReturn(duration)

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        Mockito.`when`(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(temporaryBasalInfo)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = basalRate,
            duration = duration,
            isAbsolute = true,
            type = temporaryBasalInfo.type,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsAbsoluteTempAndSameExpectedTemporaryBasalInfoThenExpectNoPumpSync() {
        // Inputs
        val basalType = BasalType.ABSOLUTE_TEMP
        val basalRate = 0.5
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime) // Ensure it's the same as input startTime

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync, Mockito.never()).syncTemporaryBasalWithPumpId(
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
        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsAbsoluteTempAndNoTemporaryBasalInfoThenExpectNewData() {
        // Inputs
        val basalType = BasalType.ABSOLUTE_TEMP
        val basalRate = 0.5
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        val temporaryBasalInfo: PumpSync.PumpState.TemporaryBasal? = null

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        Mockito.`when`(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(temporaryBasalInfo)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = basalRate,
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = null,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
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
        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        val temporaryBasalInfo: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(temporaryBasalInfo.duration).thenReturn(duration)

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        Mockito.`when`(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(temporaryBasalInfo)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        val adjustedBasalRate = (basalRate / medtrumPump.baseBasalRate) * 100
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = adjustedBasalRate,
            duration = duration,
            isAbsolute = false,
            type = temporaryBasalInfo.type,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsSuspendedThenExpectNewData() {
        // Inputs
        val basalType = BasalType.SUSPEND_MORE_THAN_MAX_PER_DAY
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        Mockito.`when`(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(null)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = basalRate,
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
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
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime) // Ensure it's the same as input startTime

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync, Mockito.never()).syncTemporaryBasalWithPumpId(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsSuspendedAndNewerFakeTBRThenExpectInvalidateAndNewData() {
        // Inputs
        val basalType = BasalType.SUSPEND_MORE_THAN_MAX_PER_DAY
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime + T.mins(10).msecs()) // Ensure it's different
        Mockito.`when`(expectedTemporaryBasal.timestamp).thenReturn(basalStartTime + T.mins(10).msecs())  // Newer Fake TBR
        Mockito.`when`(expectedTemporaryBasal.duration).thenReturn(T.mins(4800L).msecs()) // Fake TBR duration

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = expectedTemporaryBasal,
                extendedBolus = null,
                bolus = null,
                profile = null,
                serialNumber = "someSerialNumber"
            )
        )
        Mockito.`when`(temporaryBasalStorage.findTemporaryBasal(basalStartTime, basalRate)).thenReturn(null)

        // Call
        medtrumPump.handleBasalStatusUpdate(basalType, basalRate, basalSequence, basalPatchId, basalStartTime, receivedTime)

        // Expected values
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = basalStartTime,
            rate = basalRate,
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = basalStartTime,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsNoneAndThenExpectFakeTBR() {
        // Inputs
        val basalType = BasalType.NONE
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = basalRate,
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = dateUtil.now(),
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsStandardAndTempBasalExpectedThenExpectSyncStop() {
        // Inputs
        val basalType = BasalType.STANDARD
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.pumpId).thenReturn(basalStartTime - 10) // Ensure it's different

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync).syncStopTemporaryBasalWithPumpId(
            timestamp = basalStartTime + 250,
            endPumpId = basalStartTime + 250,
            pumpType = PumpType.MEDTRUM_300U,
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
    }

    @Test fun handleBasalStatusUpdateWhenBasalTypeIsStandardAndNoTempBasalExpectedThenExpectNoSyncStop() {
        // Inputs
        val basalType = BasalType.STANDARD
        val basalRate = 0.0
        val basalSequence = 123
        val basalPatchId = 1L
        val basalStartTime = 1000L
        val receivedTime = 1500L

        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync, Mockito.never()).syncStopTemporaryBasalWithPumpId(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )

        Assertions.assertEquals(basalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(basalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(basalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(basalSequence, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(basalPatchId, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(basalStartTime, medtrumPump.lastBasalStartTime)
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
        Assertions.assertEquals(newPatchId, medtrumPump.patchId)
        Assertions.assertEquals(newSequenceNumber, medtrumPump.currentSequenceNumber)
        Assertions.assertEquals(newStartTime, medtrumPump.patchStartTime)
        Assertions.assertEquals(1, medtrumPump.syncedSequenceNumber)
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
        Assertions.assertEquals(patchId, medtrumPump.lastStopPatchId)
        Assertions.assertEquals(sequence, medtrumPump.currentSequenceNumber)
    }

    @Test fun setFakeTBRIfNotSetWhenNoFakeTBRAlreadyRunningExpectPumpSync() {
        // Inputs
        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.duration).thenReturn(T.mins(30L).msecs())

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = dateUtil.now(),
            rate = 0.0,
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
        medtrumPump.deviceType = MedtrumSnUtil.MD_8301

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        Mockito.`when`(expectedTemporaryBasal.duration).thenReturn(T.mins(4800L).msecs())

        Mockito.`when`(pumpSync.expectedPumpState()).thenReturn(
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
        Mockito.verify(pumpSync, Mockito.never()).syncTemporaryBasalWithPumpId(
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
        Mockito.verify(pumpSync, Mockito.times(1)).insertTherapyEventIfNewWithTimestamp(
            newStartTime,
            DetailedBolusInfo.EventType.CANNULA_CHANGE,
            null,
            null,
            medtrumPump.pumpType(),
            medtrumPump.pumpSN.toString(radix = 16)
        )

        Mockito.verify(pumpSync, Mockito.times(1)).insertTherapyEventIfNewWithTimestamp(
            newStartTime,
            DetailedBolusInfo.EventType.INSULIN_CHANGE,
            null,
            null,
            medtrumPump.pumpType(),
            medtrumPump.pumpSN.toString(radix = 16)
        )
    }
}
