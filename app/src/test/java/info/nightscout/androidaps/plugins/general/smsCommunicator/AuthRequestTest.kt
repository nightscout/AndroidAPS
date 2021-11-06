package info.nightscout.androidaps.plugins.general.smsCommunicator

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class AuthRequestTest : TestBase() {

    @Mock lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var otp: OneTimePassword
    @Mock lateinit var dateUtil: DateUtil

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AuthRequest) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.smsCommunicatorPlugin = smsCommunicatorPlugin
                it.otp = otp
                it.dateUtil = dateUtil
            }
        }
    }

    private var sentSms: Sms? = null
    private var actionCalled = false

    @Before fun prepareTests() {
        `when`(rh.gs(R.string.sms_wrongcode)).thenReturn("Wrong code. Command cancelled.")
        doAnswer(Answer { invocation: InvocationOnMock ->
            sentSms = invocation.getArgument(0)
            null
        } as Answer<*>).`when`(smsCommunicatorPlugin).sendSMS(anyObject())
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
        Assert.assertEquals(sentSms!!.phoneNumber, "aNumber")
        Assert.assertEquals(sentSms!!.text, "Request text")

        // wrong reply
        actionCalled = false
        authRequest.action("EFG")
        Assert.assertEquals(sentSms!!.phoneNumber, "aNumber")
        Assert.assertEquals(sentSms!!.text, "Wrong code. Command cancelled.")
        Assert.assertFalse(actionCalled)

        // correct reply
        authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        actionCalled = false
        `when`(otp.checkOTP(anyObject())).thenReturn(OneTimePasswordValidationResult.OK)
        authRequest.action("ABC")
        Assert.assertTrue(actionCalled)
        // second time action should not be called
        actionCalled = false
        authRequest.action("ABC")
        Assert.assertFalse(actionCalled)

        // test timed out message
        val now: Long = 10000
        `when`(dateUtil.now()).thenReturn(now)
        authRequest = AuthRequest(injector, requester, "Request text", "ABC", action)
        actionCalled = false
        `when`(dateUtil.now()).thenReturn(now + T.mins(Constants.SMS_CONFIRM_TIMEOUT).msecs() + 1)
        authRequest.action("ABC")
        Assert.assertFalse(actionCalled)
    }
}