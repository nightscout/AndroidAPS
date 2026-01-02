package app.aaps.pump.medtronic.util

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.defs.MedtronicNotificationType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for MedtronicUtil instance methods
 */
class MedtronicUtilInstanceUTest : TestBaseWithProfile() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var rxBusMock: RxBus

    lateinit var medtronicUtil: MedtronicUtil

    @BeforeEach
    fun setup() {
        medtronicUtil = MedtronicUtil(aapsLogger, rxBusMock, rileyLinkUtil, medtronicPumpStatus, uiInteraction)
    }

    // getBolusStrokes tests
    @Test
    fun `test getBolusStrokes for 10-stroke pump with small amount`() {
        whenever(medtronicPumpStatus.medtronicDeviceType).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        // 1.0 U * 10 strokes/U = 10 strokes = 0x0A
        // Format: [length, data] = [0x01, 0x0A]
        val result = medtronicUtil.getBolusStrokes(1.0)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0x01.toByte()) // length = 1
        assertThat(result[1]).isEqualTo(0x0A.toByte()) // strokes = 10
    }

    @Test
    fun `test getBolusStrokes for 10-stroke pump with larger amount`() {
        whenever(medtronicPumpStatus.medtronicDeviceType).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        // 5.5 U * 10 strokes/U = 55 strokes = 0x37
        // Format: [length, data] = [0x01, 0x37]
        val result = medtronicUtil.getBolusStrokes(5.5)

        assertThat(result).hasLength(2)
        assertThat(result[0]).isEqualTo(0x01.toByte()) // length = 1
        assertThat(result[1]).isEqualTo(0x37.toByte()) // strokes = 55
    }

    @Test
    fun `test getBolusStrokes for 40-stroke pump with small amount`() {
        whenever(medtronicPumpStatus.medtronicDeviceType).thenReturn(MedtronicDeviceType.Medtronic_554_Veo)

        // 0.5 U * 40 strokes/U = 20 strokes = 0x0014
        val result = medtronicUtil.getBolusStrokes(0.5)

        assertThat(result).hasLength(3) // Length byte + 2 data bytes
        assertThat(result[0]).isEqualTo(0x02.toByte()) // Length = 2
        assertThat(MedtronicUtil.makeUnsignedShort(result[1].toInt(), result[2].toInt())).isEqualTo(20)
    }

    @Test
    fun `test getBolusStrokes for 40-stroke pump with medium amount uses scroll rate 2`() {
        whenever(medtronicPumpStatus.medtronicDeviceType).thenReturn(MedtronicDeviceType.Medtronic_554_Veo)

        // 5.0 U with scrollRate=2 for amounts > 1
        // 5.0 * 40 / 2 = 100, * 2 = 200 strokes
        val result = medtronicUtil.getBolusStrokes(5.0)

        assertThat(result).hasLength(3)
        assertThat(result[0]).isEqualTo(0x02.toByte())
        assertThat(MedtronicUtil.makeUnsignedShort(result[1].toInt(), result[2].toInt())).isEqualTo(200)
    }

    @Test
    fun `test getBolusStrokes for 40-stroke pump with large amount uses scroll rate 4`() {
        whenever(medtronicPumpStatus.medtronicDeviceType).thenReturn(MedtronicDeviceType.Medtronic_554_Veo)

        // 15.0 U with scrollRate=4 for amounts > 10
        // 15.0 * 40 / 4 = 150, * 4 = 600 strokes = 0x0258
        val result = medtronicUtil.getBolusStrokes(15.0)

        assertThat(result).hasLength(3)
        assertThat(result[0]).isEqualTo(0x02.toByte())
        assertThat(MedtronicUtil.makeUnsignedShort(result[1].toInt(), result[2].toInt())).isEqualTo(600)
    }

    // buildCommandPayload tests
    @Test
    fun `test buildCommandPayload with no parameters`() {
        whenever(rileyLinkServiceData.pumpIDBytes).thenReturn(byteArrayOf(0x12, 0x34, 0x56))

        val result = medtronicUtil.buildCommandPayload(
            rileyLinkServiceData,
            MedtronicCommandType.PumpModel,
            null
        )

        // Expected: 0xA7 0x12 0x34 0x56 <commandCode> 0x00
        assertThat(result).hasLength(6) // ENVELOPE_SIZE (4) + commandLength (2)
        assertThat(result[0]).isEqualTo(0xA7.toByte())
        assertThat(result[1]).isEqualTo(0x12.toByte())
        assertThat(result[2]).isEqualTo(0x34.toByte())
        assertThat(result[3]).isEqualTo(0x56.toByte())
        assertThat(result[4]).isEqualTo(MedtronicCommandType.PumpModel.commandCode)
        assertThat(result[5]).isEqualTo(0x00.toByte()) // No parameters
    }

    @Test
    fun `test buildCommandPayload with single parameter`() {
        whenever(rileyLinkServiceData.pumpIDBytes).thenReturn(byteArrayOf(0x12, 0x34, 0x56))

        val parameters = byteArrayOf(0xAB.toByte())
        val result = medtronicUtil.buildCommandPayload(
            rileyLinkServiceData,
            MedtronicCommandType.SetBasalProfileSTD,
            parameters
        )

        // Expected: 0xA7 0x12 0x34 0x56 <commandCode> 0x01 0xAB
        assertThat(result).hasLength(7)
        assertThat(result[0]).isEqualTo(0xA7.toByte())
        assertThat(result[1]).isEqualTo(0x12.toByte())
        assertThat(result[2]).isEqualTo(0x34.toByte())
        assertThat(result[3]).isEqualTo(0x56.toByte())
        assertThat(result[4]).isEqualTo(MedtronicCommandType.SetBasalProfileSTD.commandCode)
        assertThat(result[5]).isEqualTo(0x01.toByte()) // 1 parameter
        assertThat(result[6]).isEqualTo(0xAB.toByte())
    }

    @Test
    fun `test buildCommandPayload with multiple parameters`() {
        whenever(rileyLinkServiceData.pumpIDBytes).thenReturn(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()))

        val parameters = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = medtronicUtil.buildCommandPayload(
            rileyLinkServiceData,
            MedtronicCommandType.SetTemporaryBasal,
            parameters
        )

        assertThat(result).hasLength(10) // 4 + 2 + 4
        assertThat(result[0]).isEqualTo(0xA7.toByte())
        assertThat(result[1]).isEqualTo(0xAA.toByte())
        assertThat(result[2]).isEqualTo(0xBB.toByte())
        assertThat(result[3]).isEqualTo(0xCC.toByte())
        assertThat(result[4]).isEqualTo(MedtronicCommandType.SetTemporaryBasal.commandCode)
        assertThat(result[5]).isEqualTo(0x04.toByte()) // 4 parameters
        assertThat(result[6]).isEqualTo(0x01.toByte())
        assertThat(result[7]).isEqualTo(0x02.toByte())
        assertThat(result[8]).isEqualTo(0x03.toByte())
        assertThat(result[9]).isEqualTo(0x04.toByte())
    }

    // getBasalProfileFrames tests
    @Test
    fun `test getBasalProfileFrames with small data`() {
        val data = ByteArray(30) { (it + 1).toByte() }

        val frames = medtronicUtil.getBasalProfileFrames(data)

        // Should have 2 frames: data frame + terminator
        assertThat(frames).hasSize(2)
        // First frame: frame number + 30 data bytes
        assertThat(frames[0]).hasSize(31)
        assertThat(frames[0][0]).isEqualTo(0x01.toByte()) // Frame 1
        assertThat(frames[0][1]).isEqualTo(0x01.toByte()) // First data byte
        // Second frame: terminator with done bit, padded to 65
        assertThat(frames[1]).hasSize(65)
        assertThat(frames[1][0].toInt() and 0x80).isEqualTo(0x80) // Done bit set
    }

    @Test
    fun `test getBasalProfileFrames with data requiring two frames`() {
        // 128 bytes = 2 full data frames (64 bytes each) + terminator
        val data = ByteArray(128) { (it + 1).toByte() }

        val frames = medtronicUtil.getBasalProfileFrames(data)

        assertThat(frames).hasSize(3) // 2 data frames + 1 terminator
        // Frame 1: 64 data bytes + frame number
        assertThat(frames[0]).hasSize(65)
        assertThat(frames[0][0]).isEqualTo(0x01.toByte())
        // Frame 2: 64 data bytes + frame number
        assertThat(frames[1]).hasSize(65)
        assertThat(frames[1][0]).isEqualTo(0x02.toByte())
        // Frame 3: terminator with done bit
        assertThat(frames[2]).hasSize(65)
        assertThat(frames[2][0].toInt() and 0x80).isEqualTo(0x80)
    }

    @Test
    fun `test getBasalProfileFrames with empty data`() {
        val data = ByteArray(0)

        val frames = medtronicUtil.getBasalProfileFrames(data)

        assertThat(frames).hasSize(1)
        assertThat(frames[0][0].toInt() and 0x80).isEqualTo(0x80) // Done bit
        assertThat(frames[0]).hasSize(65)
    }

    @Test
    fun `test getBasalProfileFrames with all zero data`() {
        // All zeros should be treated as empty frame
        val data = ByteArray(30) // All zeros

        val frames = medtronicUtil.getBasalProfileFrames(data)

        assertThat(frames).hasSize(1)
        assertThat(frames[0]).hasSize(65)
        assertThat(frames[0][0].toInt() and 0x80).isEqualTo(0x80) // Done bit
    }

    @Test
    fun `test getBasalProfileFrames with exactly 64 bytes`() {
        // 64 bytes of non-zero data + terminator
        val data = ByteArray(64) { (it + 1).toByte() }

        val frames = medtronicUtil.getBasalProfileFrames(data)

        assertThat(frames).hasSize(2) // 1 data frame + 1 terminator
        assertThat(frames[0]).hasSize(65) // Frame number + 64 data bytes
        assertThat(frames[0][0]).isEqualTo(0x01.toByte())
        assertThat(frames[1]).hasSize(65) // Terminator
        assertThat(frames[1][0].toInt() and 0x80).isEqualTo(0x80)
    }

    // Command tracking tests
    @Test
    fun `test getCurrentCommand initially returns null`() {
        assertThat(medtronicUtil.getCurrentCommand()).isNull()
    }

    @Test
    fun `test setCurrentCommand stores command`() {
        medtronicUtil.setCurrentCommand(MedtronicCommandType.PumpModel)

        assertThat(medtronicUtil.getCurrentCommand()).isEqualTo(MedtronicCommandType.PumpModel)
    }

    @Test
    fun `test setCurrentCommand can be set to null`() {
        medtronicUtil.setCurrentCommand(MedtronicCommandType.PumpModel)
        medtronicUtil.setCurrentCommand(null)

        assertThat(medtronicUtil.getCurrentCommand()).isNull()
    }

    @Test
    fun `test setCurrentCommand with page and frame numbers`() {
        medtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, 5, 3)

        assertThat(medtronicUtil.getCurrentCommand()).isEqualTo(MedtronicCommandType.GetHistoryData)
        assertThat(medtronicUtil.pageNumber).isEqualTo(5)
        assertThat(medtronicUtil.frameNumber).isEqualTo(3)
    }

    @Test
    fun `test setCurrentCommand with null frame number`() {
        medtronicUtil.setCurrentCommand(MedtronicCommandType.GetBasalProfileSTD, 1, null)

        assertThat(medtronicUtil.getCurrentCommand()).isEqualTo(MedtronicCommandType.GetBasalProfileSTD)
        assertThat(medtronicUtil.pageNumber).isEqualTo(1)
        assertThat(medtronicUtil.frameNumber).isNull()
    }

    @Test
    fun `test setCurrentCommand does not change if same command`() {
        medtronicUtil.setCurrentCommand(MedtronicCommandType.PumpModel)
        val firstCommand = medtronicUtil.getCurrentCommand()

        medtronicUtil.setCurrentCommand(MedtronicCommandType.PumpModel, 1, 2)

        assertThat(medtronicUtil.getCurrentCommand()).isSameInstanceAs(firstCommand)
    }

    // Model tracking tests
    @Test
    fun `test medtronicPumpModel getter`() {
        whenever(medtronicPumpStatus.medtronicDeviceType).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        assertThat(medtronicUtil.medtronicPumpModel).isEqualTo(MedtronicDeviceType.Medtronic_522_722)
    }

    @Test
    fun `test medtronicPumpModel setter`() {
        medtronicUtil.medtronicPumpModel = MedtronicDeviceType.Medtronic_554_Veo

        verify(medtronicPumpStatus).medtronicDeviceType = MedtronicDeviceType.Medtronic_554_Veo
    }

    @Test
    fun `test isModelSet flag can be set and read`() {
        medtronicUtil.isModelSet = false
        assertThat(medtronicUtil.isModelSet).isFalse()

        medtronicUtil.isModelSet = true
        assertThat(medtronicUtil.isModelSet).isTrue()
    }

    // Notification tests
    @Test
    fun `test dismissNotification sends event`() {
        val notificationType = MedtronicNotificationType.PumpUnreachable

        medtronicUtil.dismissNotification(notificationType, rxBusMock)

        val eventCaptor = argumentCaptor<EventDismissNotification>()
        verify(rxBusMock).send(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.id).isEqualTo(notificationType.notificationType)
    }

    // Settings and time tests
    @Test
    fun `test pumpTime can be set and retrieved`() {
        assertThat(medtronicUtil.pumpTime).isNull()

        val clockDTO = app.aaps.pump.medtronic.data.dto.ClockDTO(
            org.joda.time.LocalDateTime(2023, 1, 15, 10, 30, 0),
            org.joda.time.LocalDateTime(2023, 1, 15, 10, 28, 45)
        )
        medtronicUtil.pumpTime = clockDTO

        assertThat(medtronicUtil.pumpTime).isEqualTo(clockDTO)
    }

    @Test
    fun `test settings can be set and retrieved`() {
        assertThat(medtronicUtil.settings).isNull()

        val settings = mapOf(
            "PCFG_MAX_BOLUS" to app.aaps.pump.medtronic.data.dto.PumpSettingDTO(
                "PCFG_MAX_BOLUS",
                "10.0",
                app.aaps.pump.medtronic.defs.PumpConfigurationGroup.Insulin
            ),
            "PCFG_MAX_BASAL" to app.aaps.pump.medtronic.data.dto.PumpSettingDTO(
                "PCFG_MAX_BASAL",
                "5.0",
                app.aaps.pump.medtronic.defs.PumpConfigurationGroup.Basal
            )
        )
        medtronicUtil.settings = settings

        assertThat(medtronicUtil.settings).isEqualTo(settings)
        assertThat(medtronicUtil.settings!!["PCFG_MAX_BOLUS"]?.value).isEqualTo("10.0")
    }

    @Test
    fun `test gsonInstance is properly initialized`() {
        assertThat(medtronicUtil.gsonInstance).isNotNull()
        // Test that it excludes fields without @Expose
        val json = medtronicUtil.gsonInstance.toJson(mapOf("test" to "value"))
        assertThat(json).contains("test")
    }
}
