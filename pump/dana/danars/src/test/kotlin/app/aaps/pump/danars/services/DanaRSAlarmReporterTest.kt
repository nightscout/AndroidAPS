package app.aaps.pump.danars.services

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

class DanaRSAlarmReporterTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaPump: DanaPump

    private lateinit var sut: DanaRSAlarmReporter

    @BeforeEach
    fun setup() {
        whenever(rh.gs(R.string.pumperror)).thenReturn("Pump Error")
        whenever(rh.gs(R.string.lowbattery)).thenReturn("Low Battery")
        whenever(rh.gs(R.string.occlusion)).thenReturn("Occlusion")
        whenever(rh.gs(R.string.danai2_pump_check_error)).thenReturn("Pump check error")
        whenever(rh.gs(R.string.pumpshutdown)).thenReturn("Pump Shutdown")
        whenever(danaPump.serialNumber).thenReturn("CFL00001TJ")
        sut = DanaRSAlarmReporter(rh, notificationManager, pumpSync, danaPump)
    }

    /** Error flags from the Dana-i2 "PUMP" reply, spec page "Encryption Response 0x02" */
    @ParameterizedTest
    @CsvSource(
        "1, Low Battery",
        "2, Occlusion",
        "4, Pump Error",      // System Error
        "8, Pump Error",      // System Error (I2C)
        "16, Pump check error",
        "32, Pump Shutdown",
        "64, Pump Error",     // System Error (Internal Memory)
        "0, Pump Error",      // no flag set
        "34, Occlusion"       // more flags: the first one in the list wins
    )
    fun errorFlagsGiveTheTextFromTheSpec(flags: Int, text: String) {
        assertThat(sut.pumpCheckErrorText(flags)).isEqualTo(text)
    }

    @Test
    fun replyWithoutErrorByteIsPumpError() {
        assertThat(sut.pumpCheckErrorText(null)).isEqualTo("Pump Error")
    }

    @Test
    fun reportPostsTheAlarmAndStoresAnAnnouncement() {
        sut.report("Occlusion")

        verify(notificationManager).post(eq(NotificationId.DANA_PUMP_ALARM), eq("Occlusion"), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
        verifyBlocking(pumpSync) { insertAnnouncement(eq("Occlusion"), anyOrNull(), anyOrNull(), eq("CFL00001TJ")) }
    }
}
