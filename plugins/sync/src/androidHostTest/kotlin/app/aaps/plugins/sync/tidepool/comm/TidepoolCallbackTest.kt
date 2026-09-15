package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import com.google.common.truth.Truth.assertThat
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [TidepoolCallback], the shared Retrofit callback of all Tidepool calls. It decides what
 * counts as success (2xx with a body), fills the [Session] from the reply, and reports every other
 * result to the log view.
 */
class TidepoolCallbackTest {

    private val aapsLogger: AAPSLogger = mock()
    private val rxBus: RxBus = mock()
    private val call: Call<AuthReplyMessage?> = mock()
    private val session = Session(SESSION_TOKEN_HEADER, null)

    private val succeeded = CountDownLatch(1)
    private val failed = CountDownLatch(1)

    private val sut = TidepoolCallback<AuthReplyMessage?>(
        aapsLogger, rxBus, session, "Test call",
        onSuccess = { succeeded.countDown() },
        onFail = { failed.countDown() }
    )

    // The callback does its work on Dispatchers.IO, so every test waits for the result.
    private fun CountDownLatch.awaitResult() = await(5, TimeUnit.SECONDS)

    private fun statusMessages(): List<String> {
        val captor = argumentCaptor<EventTidepoolStatus>()
        verify(rxBus).send(captor.capture())
        return captor.allValues.map { it.status }
    }

    @Test
    fun `successful reply fills the session and reports success`() {
        val reply = AuthReplyMessage()

        sut.onResponse(call, Response.success<AuthReplyMessage?>(reply, headersOf(SESSION_TOKEN_HEADER, "token-123")))

        assertThat(succeeded.awaitResult()).isTrue()
        assertThat(session.authReply).isEqualTo(reply)
        assertThat(session.token).isEqualTo("token-123")
    }

    @Test
    fun `error code reports failure with the code in the message`() {
        sut.onResponse(call, Response.error(401, "denied".toResponseBody("text/plain".toMediaTypeOrNull())))

        assertThat(failed.awaitResult()).isTrue()
        assertThat(statusMessages().single()).contains("401")
        assertThat(session.authReply).isNull()
    }

    @Test
    fun `empty body reports failure even with a success code`() {
        sut.onResponse(call, Response.success<AuthReplyMessage?>(null))

        assertThat(failed.awaitResult()).isTrue()
        assertThat(statusMessages().single()).contains("Test call")
    }

    @Test
    fun `network failure reports failure with the reason`() {
        sut.onFailure(call, IOException("no route to host"))

        assertThat(failed.awaitResult()).isTrue()
        assertThat(statusMessages().single()).contains("no route to host")
    }
}
