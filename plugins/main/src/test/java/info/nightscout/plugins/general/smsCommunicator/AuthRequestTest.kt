package info.nightscout.plugins.general.smsCommunicator

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.smsCommunicator.Sms
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.plugins.R
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(sentSms!!.phoneNumber, "aNumber")
        Assertions.assertEquals(sentSms!!.text, "Request text")

        // wrong reply
        actionCalled = false
        authRequest.action("EFG")
        Assertions.assertEquals(sentSms!!.phoneNumber, "aNumber")
        Assertions.assertEquals(sentSms!!.text, "Wrong code. Command cancelled.")
        Assertions.assertFalse(actionCalled)

        // correct reply
        authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        actionCalled = false
        `when`(otp.checkOTP(anyObject())).thenReturn(OneTimePasswordValidationResult.OK)
        authRequest.action("ABC")
        Assertions.assertTrue(actionCalled)
        // second time action should not be called
        actionCalled = false
        authRequest.action("ABC")
        Assertions.assertFalse(actionCalled)

        // test timed out message
        val now: Long = 10000
        `when`(dateUtil.now()).thenReturn(now)
        authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        actionCalled = false
        `when`(dateUtil.now()).thenReturn(now + T.mins(Constants.SMS_CONFIRM_TIMEOUT).msecs() + 1)
        authRequest.action("ABC")
        Assertions.assertFalse(actionCalled)
    }
}