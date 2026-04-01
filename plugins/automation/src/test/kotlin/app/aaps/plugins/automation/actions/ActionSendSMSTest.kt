package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionSendSMSTest : ActionsTestBase() {

    private lateinit var sut: ActionSendSMS

    @BeforeEach
    fun setup() {

        whenever(rh.gs(R.string.sendsmsactionlabel)).thenReturn("Send SMS: %s")
        whenever(rh.gs(R.string.sendsmsactiondescription)).thenReturn("Send SMS to all numbers")

        sut = ActionSendSMS(injector)
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(R.string.sendsmsactiondescription)
    }

    @Test fun shortDescriptionTest() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Send SMS: ")
    }

    @Test fun iconTest() = runTest {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_notifications)
    }

    @Test fun doActionTest() = runTest {
        whenever(smsCommunicator.sendNotificationToAllNumbers(anyString())).thenReturn(true)
        sut.text = InputString("Asd")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.text = InputString("Asd")
        JSONAssert.assertEquals("""{"data":{"text":"Asd"},"type":"ActionSendSMS"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"text":"Asd"}""")
        assertThat(sut.text.value).isEqualTo("Asd")
    }
}
