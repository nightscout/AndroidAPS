package info.nightscout.plugins.general.smsCommunicator

import android.telephony.SmsMessage
import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.smsCommunicator.Sms
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SmsTest : TestBase() {

    @Test fun doTests() {
        val smsMessage = Mockito.mock(SmsMessage::class.java)
        `when`(smsMessage.originatingAddress).thenReturn("aNumber")
        `when`(smsMessage.messageBody).thenReturn("aBody")
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

        // copy constructor #1
        val sms2 = Sms(sms)
        Assert.assertEquals(sms2.phoneNumber, "aNumber")
        Assert.assertEquals(sms2.text, "U")
        Assert.assertTrue(sms2.sent)
        Assert.assertEquals(sms2.toString(), "SMS from aNumber: U")

        // copy constructor #2
        val sms3 = Sms(sms, "different")
        Assert.assertEquals(sms3.phoneNumber, "different")
        Assert.assertEquals(sms3.text, "U")
        Assert.assertTrue(sms3.sent)
        Assert.assertEquals(sms3.toString(), "SMS from different: U")

    }
}