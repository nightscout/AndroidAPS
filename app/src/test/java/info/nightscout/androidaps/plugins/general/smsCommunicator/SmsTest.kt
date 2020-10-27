package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.telephony.SmsMessage
import info.nightscout.androidaps.TestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(SmsMessage::class)
class SmsTest : TestBase() {

    @Test fun doTests() {
        val smsMessage = PowerMockito.mock(SmsMessage::class.java)
        PowerMockito.`when`(smsMessage.originatingAddress).thenReturn("aNumber")
        PowerMockito.`when`(smsMessage.messageBody).thenReturn("aBody")
        var sms = Sms(smsMessage)
        Assert.assertEquals(sms.phoneNumber, "aNumber")
        Assert.assertEquals(sms.text, "aBody")
        Assert.assertTrue(sms.received)
        sms = Sms("aNumber", "aBody")
        Assert.assertEquals(sms.phoneNumber, "aNumber")
        Assert.assertEquals(sms.text, "aBody")
        Assert.assertTrue(sms.sent)
        sms = Sms("aNumber", "U")
        Assert.assertEquals(sms.phoneNumber, "aNumber")
        Assert.assertEquals(sms.text, "U")
        Assert.assertTrue(sms.sent)
        Assert.assertEquals(sms.toString(), "SMS from aNumber: U")
    }
}