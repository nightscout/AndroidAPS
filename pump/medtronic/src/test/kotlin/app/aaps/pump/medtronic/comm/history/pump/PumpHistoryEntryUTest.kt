package app.aaps.pump.medtronic.comm.history.pump

import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.util.MedtronicUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Created by andy on 4/9/19.
 *
 */
class PumpHistoryEntryUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setUp() {
        medtronicUtil = MedtronicUtil(aapsLogger, rxBus, rileyLinkUtil, medtronicPumpStatus, uiInteraction)
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_723_Revel)
        decoder = MedtronicPumpHistoryDecoder(aapsLogger, medtronicUtil)
    }

    @Test
    fun checkIsAfter() {
        val dateObject = 20191010000000L
        val queryObject = 20191009000000L
        val phe = PumpHistoryEntry()
        phe.atechDateTime = dateObject
        assertThat(phe.isAfter(queryObject)).isTrue()
    }

    @Test
    fun decodeBgReceived() {
        val bgRecord = getPumpHistoryEntryFromData(
            // head
            0x39, 0x15,
            // datetime (combined with glucose in mg/dl)
            0xC2, 0x25, 0xF3, 0x61, 0x17,
            // serial number
            0x12, 0x34, 0x56
        )
        val expectedGlucoseMgdl = 175
        val expectedMeterSerial = "123456"

        assertThat(bgRecord.getDecodedDataEntry("GlucoseMgdl")).isEqualTo(expectedGlucoseMgdl)
        assertThat(bgRecord.getDecodedDataEntry("MeterSerial")).isEqualTo(expectedMeterSerial)
    }
}
