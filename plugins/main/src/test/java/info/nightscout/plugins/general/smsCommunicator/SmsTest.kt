package info.nightscout.plugins.general.smsCommunicator

import android.telephony.SmsMessage
import info.nightscout.interfaces.smsCommunicator.Sms
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SmsTest : TestBase() {

    @Test fun doTests() {
        val smsMessage = Mockito.mock(SmsMessage::class.java)
        `when`(smsMessage.originatingAddress).thenReturn("aNumber")
        `when`(smsMessage.messageBody).thenReturn("aBody")
        var sms = Sms(smsMessage)
        Assertions.assertEquals(sms.phoneNumber, "aNumber")
        Assertions.assertEquals(sms.text, "aBody")
        Assertions.assertTrue(sms.received)
        sms = Sms("aNumber", "aBody")
        Assertions.assertEquals(sms.phoneNumber, "aNumber")
        Assertions.assertEquals(sms.text, "aBody")
        Assertions.assertTrue(sms.sent)
        sms = Sms("aNumber", "U")
        Assertions.assertEquals(sms.phoneNumber, "aNumber")
        Assertions.assertEquals(sms.text, "U")
        Assertions.assertTrue(sms.sent)
        Assertions.assertEquals(sms.toString(), "SMS from aNumber: U")

        // copy constructor #1
        val sms2 = Sms(sms)
        Assertions.assertEquals(sms2.phoneNumber, "aNumber")
        Assertions.assertEquals(sms2.text, "U")
        Assertions.assertTrue(sms2.sent)
        Assertions.assertEquals(sms2.toString(), "SMS from aNumber: U")

        // copy constructor #2
        val sms3 = Sms(sms, "different")
        Assertions.assertEquals(sms3.phoneNumber, "different")
        Assertions.assertEquals(sms3.text, "U")
        Assertions.assertTrue(sms3.sent)
        Assertions.assertEquals(sms3.toString(), "SMS from different: U")

    }
}