package app.aaps.plugins.main.general.smsCommunicator

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePassword
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePasswordValidationResult
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class AuthRequestTest : TestBase() {

    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var otp: OneTimePassword
    @Mock lateinit var dateUtil: DateUtil

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AuthRequest) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.smsCommunicator = smsCommunicator
                it.otp = otp
                it.dateUtil = dateUtil
            }
        }
    }

    private var sentSms: Sms? = null
    private var actionCalled = false

    @BeforeEach fun prepareTests() {
        `when`(rh.gs(R.string.sms_wrong_code)).thenReturn("Wrong code. Command cancelled.")
        doAnswer(Answer { invocation: InvocationOnMock ->
            sentSms = invocation.getArgument(0)
            null
        } as Answer<*>).`when`(smsCommunicator).sendSMS(anyObject())
    }

    @Test fun doTests() {
        val requester = Sms("aNumber", "aText")
        val action: SmsAction = object : SmsAction(false) {
            override fun run() {
                actionCalled = true
            }
        }

        // Check if SMS requesting code is sent
        var authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        assertThat(sentSms!!.phoneNumber).isEqualTo("aNumber")
        assertThat(sentSms!!.text).isEqualTo("Request text")

        // wrong reply
        actionCalled = false
        authRequest.action("EFG")
        assertThat(sentSms!!.phoneNumber).isEqualTo("aNumber")
        assertThat(sentSms!!.text).isEqualTo("Wrong code. Command cancelled.")
        assertThat(actionCalled).isFalse()

        // correct reply
        authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        actionCalled = false
        `when`(otp.checkOTP(anyObject())).thenReturn(OneTimePasswordValidationResult.OK)
        authRequest.action("ABC")
        assertThat(actionCalled).isTrue()
        // second time action should not be called
        actionCalled = false
        authRequest.action("ABC")
        assertThat(actionCalled).isFalse()

        // test timed out message
        val now: Long = 10000
        `when`(dateUtil.now()).thenReturn(now)
        authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        actionCalled = false
        `when`(dateUtil.now()).thenReturn(now + T.mins(Constants.SMS_CONFIRM_TIMEOUT).msecs() + 1)
        authRequest.action("ABC")
        assertThat(actionCalled).isFalse()
    }
}
