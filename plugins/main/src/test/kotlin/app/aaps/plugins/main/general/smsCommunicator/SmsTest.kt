package app.aaps.plugins.main.general.smsCommunicator

import android.telephony.SmsMessage
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SmsTest : TestBase() {

    @Test fun doTests() {
        val smsMessage: SmsMessage = mock()
        whenever(smsMessage.originatingAddress).thenReturn("aNumber")
        whenever(smsMessage.messageBody).thenReturn("aBody")
        var sms = Sms(smsMessage)
        assertThat(sms.phoneNumber).isEqualTo("aNumber")
        assertThat(sms.text).isEqualTo("aBody")
        assertThat(sms.received).isTrue()
        sms = Sms("aNumber", "aBody")
        assertThat(sms.phoneNumber).isEqualTo("aNumber")
        assertThat(sms.text).isEqualTo("aBody")
        assertThat(sms.sent).isTrue()
        sms = Sms("aNumber", "U")
        assertThat(sms.phoneNumber).isEqualTo("aNumber")
        assertThat(sms.text).isEqualTo("U")
        assertThat(sms.sent).isTrue()
        assertThat(sms.toString()).isEqualTo("SMS from aNumber: U")

        // copy constructor #1
        val sms2 = Sms(sms)
        assertThat(sms2.phoneNumber).isEqualTo("aNumber")
        assertThat(sms2.text).isEqualTo("U")
        assertThat(sms2.sent).isTrue()
        assertThat(sms2.toString()).isEqualTo("SMS from aNumber: U")

        // copy constructor #2
        val sms3 = Sms(sms, "different")
        assertThat(sms3.phoneNumber).isEqualTo("different")
        assertThat(sms3.text).isEqualTo("U")
        assertThat(sms3.sent).isTrue()
        assertThat(sms3.toString()).isEqualTo("SMS from different: U")

    }
}
