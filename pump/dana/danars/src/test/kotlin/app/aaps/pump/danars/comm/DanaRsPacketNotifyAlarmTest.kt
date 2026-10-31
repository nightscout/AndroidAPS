package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.dana.R
import app.aaps.pump.danars.DanaRSTestBase
import app.aaps.pump.danars.services.DanaRSAlarmReporter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DanaRsPacketNotifyAlarmTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync

    @Test
    fun runTest() {
        val packet = DanaRSPacketNotifyAlarm(aapsLogger, rh, DanaRSAlarmReporter(rh, notificationManager, pumpSync, danaPump))
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(17, 0x01.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // no error
        packet.handleMessage(createArray(17, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("NOTIFY__ALARM", packet.friendlyName)
    }

    /** Alarm codes from the Dana-i protocol spec, Notify 0x03 */
    @ParameterizedTest
    @CsvSource("2, Pump Error", "3, Occlusion", "4, Low Battery", "5, Pump Shutdown")
    fun alarmCodeShowsTheTextFromTheSpec(code: Int, text: String) {
        whenever(rh.gs(R.string.pumperror)).thenReturn("Pump Error")
        whenever(rh.gs(R.string.occlusion)).thenReturn("Occlusion")
        whenever(rh.gs(R.string.lowbattery)).thenReturn("Low Battery")
        whenever(rh.gs(R.string.pumpshutdown)).thenReturn("Pump Shutdown")
        val packet = DanaRSPacketNotifyAlarm(aapsLogger, rh, DanaRSAlarmReporter(rh, notificationManager, pumpSync, danaPump))

        packet.handleMessage(byteArrayOf(0xC3.toByte(), 0x03, code.toByte()))

        verify(notificationManager).post(eq(NotificationId.DANA_PUMP_ALARM), eq(text), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
    }
}
