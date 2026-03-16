package app.aaps.pump.medtrum.comm.packets

import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetRecordPacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

    /** Test packet specific behavior */

    @Mock private lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is GetRecordPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.pumpSync = pumpSync
                it.detailedBolusInfoStorage = detailedBolusInfoStorage
                it.dateUtil = dateUtil
                it.medtrumTimeUtil = medtrumTimeUtil
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val recordIndex = 4
        medtrumPump.patchId = 146

        // Call
        val packet = GetRecordPacket(packetInjector, recordIndex)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(99, 4, 0, -110, 0)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val data = byteArrayOf(35, 99, 9, 1, 0, 0, -86, 28, 2, -1, -5, -40, -27, -18, 14, 0, -64, 1, -91, -20, -82, 17, -91, -20, -82, 17, 1, 0, 26, 0, 0, 0, -102, 0, -48)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val data = byteArrayOf(35, 99, 9, 1, 0, 0, -86, 28, 2, -1, -5, -40, -27, -18, 14, 0, -64)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }

    @Test fun handleResponseGivenBolusRecordWhenAndDetailedBolusInfoPresentThenExpectPumpSyncWithTempId() {
        val data = byteArrayOf(47, 99, 10, 1, 0, 0, -86, 40, 1, -1, 38, 105, -77, 57, 56, 0, 29, 0, 1, 0, 0, 0, -82, -85, 62, 18, 22, 0, 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 76)

        val timestamp = 1694631470000L
        val bolusType = BS.Type.SMB
        val amount = 1.1

        // Mocks
        val detailedBolusInfo: DetailedBolusInfo = mock()
        detailedBolusInfo.timestamp = timestamp // Wierd way to mock but this is a @JvmField
        whenever(detailedBolusInfo.bolusType).thenReturn(bolusType)

        whenever(detailedBolusInfoStorage.findDetailedBolusInfo(timestamp, amount)).thenReturn(detailedBolusInfo)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncBolusWithTempId(
            timestamp = timestamp,
            amount = PumpInsulin(amount),
            temporaryId = timestamp,
            type = bolusType,
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(medtrumPump.lastBolusTime).isEqualTo(timestamp)
        assertThat(medtrumPump.lastBolusAmount).isWithin(0.01).of(amount)
    }

    @Test fun handleResponseGivenBolusRecordWhenAndNoDetailedBolusInfoPresentThenExpectPumpSyncWithPumpId() {
        val data = byteArrayOf(47, 99, 10, 1, 0, 0, -86, 40, 1, -1, 38, 105, -77, 57, 56, 0, 29, 0, 1, 0, 0, 0, -82, -85, 62, 18, 22, 0, 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 76)

        val timestamp = 1694631470000L
        val amount = 1.1

        // Mocks
        whenever(detailedBolusInfoStorage.findDetailedBolusInfo(timestamp, amount)).thenReturn(null)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncBolusWithPumpId(
            timestamp = timestamp,
            amount = PumpInsulin(amount),
            type = null,
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(medtrumPump.lastBolusTime).isEqualTo(timestamp)
        assertThat(medtrumPump.lastBolusAmount).isWithin(0.01).of(amount)
    }

    @Test fun handleResponseGivenExtendedBolusRecordThenExpectPumpSyncWithPumpId() {
        val data = byteArrayOf(47, 99, 5, 1, 0, 0, -86, 40, 1, -1, 38, 105, -77, 57, 63, 0, 6, 0, 2, 0, 0, 0, -22, -123, 67, 18, 0, 0, 0, 0, 25, 0, 30, 0, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -124)

        val timestamp = 1694949482000
        val amount = 1.25
        val duration = T.mins(30).msecs()

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncExtendedBolusWithPumpId(
            timestamp = timestamp,
            rate = PumpRate(amount),
            duration = duration,
            isEmulatingTB = false,
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenComboBolusRecordWhenAndNoDetailedBolusInfoPresentThenExpectPumpSyncWithPumpId() {
        val data = byteArrayOf(47, 99, 5, 1, 0, 0, -86, 40, 1, -1, 38, 105, -77, 57, 63, 0, 8, 0, 3, 0, 0, 0, 111, -110, 67, 18, 40, 0, 40, 0, 20, 0, 30, 0, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -122)

        val timestamp = 1694952687000L
        val amountDirect = 2.0
        val amountExtended = 1.0
        val duration = T.mins(30).msecs()

        // Mocks
        whenever(detailedBolusInfoStorage.findDetailedBolusInfo(timestamp, amountDirect)).thenReturn(null)

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncBolusWithPumpId(
            timestamp = timestamp,
            amount = PumpInsulin(amountDirect),
            type = null,
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )
        verify(pumpSync).syncExtendedBolusWithPumpId(
            timestamp = timestamp,
            rate = PumpRate(amountExtended),
            duration = duration,
            isEmulatingTB = false,
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenBasalRecordWhenAbsoluteTempThenExpectPumpSync() {
        val data = byteArrayOf(35, 99, 7, 1, 0, 0, -86, 28, 2, -1, 38, 105, -77, 57, 56, 0, 30, 0, -85, -85, 62, 18, -34, -84, 62, 18, 6, 0, 69, 0, 6, 0, 69, 0, -125)

        val startTime = 1694631467000
        val endTime = 1694631774000
        val rate = 3.45
        val duration = endTime - startTime

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = PumpRate(rate),
            duration = duration,
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.NORMAL,
            pumpId = startTime,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenBasalRecordWhenRelativeTempThenExpectPumpSync() {
        val data = byteArrayOf(35, 99, 7, 1, 0, 0, -86, 28, 2, -1, 38, 105, -77, 57, 63, 0, 4, 0, -116, -123, 67, 18, 81, -119, 67, 18, 7, 0, 4, 0, 1, 0, -56, 0, 1)

        val startTime = 1694949388000
        val endTime = 1694950353000
        val rate = 200.0
        val duration = endTime - startTime

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = PumpRate(rate),
            duration = duration,
            isAbsolute = false,
            type = PumpSync.TemporaryBasalType.NORMAL,
            pumpId = startTime,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenBasalRecordWhenSuspendThenExpectPumpSync() {
        // Note: This is not a real response as I was unable to get this response from any of my pumpbases, but it can theoretically happen
        val data = byteArrayOf(35, 99, 7, 1, 0, 0, -86, 28, 2, -1, -39, -7, 118, -86, -85, 1, 8, 0, -4, 116, -16, 17, 21, 125, -16, 17, 18, 0, 0, 0, 0, 0, 0, 0, 125)
        val startTime = 1689505660000
        val endTime = 1689507733000
        val rate = 0.0
        val duration = endTime - startTime

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = startTime,
            rate = PumpRate(rate),
            duration = duration,
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = startTime,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenBasalRecordWhenStandardAndSuspendEndReasonThenExpectPumpSync() {
        val data = byteArrayOf(35, 99, 8, 1, 0, 0, -86, 28, 2, -1, -39, -7, 118, -86, -85, 1, 4, 0, -117, 113, -16, 17, 9, 116, -16, 17, 1, 4, 10, 0, 2, 0, 0, 0, 57)
        val endTime = 1689505417000

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
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Just check the pumpSync here, rest of the behaviour of medtrumPump is tested in MedtrumPumpTest
        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = endTime,
            rate = PumpRate(0.0),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = endTime,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenBasalRecordWhenTempAndSuspendEndReasonThenExpectPumpSync() {
        val data = byteArrayOf(35, 99, 8, 1, 0, 0, -86, 28, 2, -1, -39, -7, 118, -86, -82, 1, 5, 0, 75, 24, -14, 17, 44, 27, -14, 17, 6, 4, 16, 0, 3, 0, 16, 0, -73)
        val endTime = 1689613740000

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
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Just check the pumpSync here, rest of the behaviour of medtrumPump is tested in MedtrumPumpTest
        // Expected values
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            timestamp = endTime,
            rate = PumpRate(0.0),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = endTime,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenBasalRecordWhenStandardAndSuspendEndReasonAndNewerExistingTBRThenExpectNoPumpSync() {
        val data = byteArrayOf(35, 99, 8, 1, 0, 0, -86, 28, 2, -1, -39, -7, 118, -86, -85, 1, 4, 0, -117, 113, -16, 17, 9, 116, -16, 17, 1, 4, 10, 0, 2, 0, 0, 0, 57)
        val endTime = 1689505417000

        // Mocks
        val expectedTemporaryBasal: PumpSync.PumpState.TemporaryBasal = mock()
        whenever(expectedTemporaryBasal.timestamp).thenReturn(endTime + T.mins(1).msecs()) // Existing temp basal is newer
        whenever(expectedTemporaryBasal.duration).thenReturn(endTime + T.mins(30).msecs()) // Normal TBR

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
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Just check the pumpSync here, rest of the behaviour of medtrumPump is tested in MedtrumPumpTest
        // Expected values
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(
            timestamp = endTime,
            rate = PumpRate(0.0),
            duration = T.mins(4800L).msecs(),
            isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
            pumpId = endTime,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenTDDRecordThenExpectPumpSync() {
        val data = byteArrayOf(
            87, 99, 8, 1, 0, 0, -86, 80, 9, -1, 38, 105, -77, 57, 56, 0, 82, 0, -32, -124, 61, 18, 120, 0, -120, 5, 0, 0, 0, 0, -102, -103, 84, 66, 0, 0,
            -120, 65, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 102, -26, -128, 66, 58, -52, -75, 63, 0, 0, -16, 66, -49, -9, -13, 63, -103, -103, 121, 66, 55,
            -75, -84, 63, 0, 0, -16, 66, -49, -9, -13, 63, 0, 0, 0, 0, -128
        )
        val timestamp = 1694556000000L
        val tdd = 53.150001525878906
        val basalTdd = 17.0
        val bolusTdd = tdd - basalTdd

        // Call
        val packet = GetRecordPacket(packetInjector, 0)
        val result = packet.handleResponse(data)

        // Expected values
        verify(pumpSync).createOrUpdateTotalDailyDose(
            timestamp = timestamp,
            bolusAmount = bolusTdd,
            basalAmount = basalTdd,
            totalAmount = tdd,
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }
}
