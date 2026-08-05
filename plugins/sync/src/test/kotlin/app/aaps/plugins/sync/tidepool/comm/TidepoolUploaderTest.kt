package app.aaps.plugins.sync.tidepool.comm

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.after
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests the state handling of [TidepoolUploader]: when it may talk to Tidepool, when it starts a new
 * login and what it does without an open dataset. The calls to the Tidepool API itself need a server
 * and stay out of scope; [TidepoolUploaderAuthTest] covers the token refresh.
 *
 * Runs under Robolectric because the uploader takes a wake lock from the real [Context].
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [35])
class TidepoolUploaderTest {

    private val aapsLogger: AAPSLogger = mock()
    private val rxBus: RxBus = mock()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences: Preferences = mock()
    private val uploadChunk: UploadChunk = mock()
    private val dateUtil: DateUtil = mock()
    private val receiverDelegate: ReceiverDelegate = mock()
    private val config: Config = mock()
    private val l: L = mock()
    private val authFlowOut: AuthFlowOut = mock()
    private val authState: AuthState = mock()
    private val authService: AuthorizationService = mock()

    private val now = 1_700_000_000_000L

    private lateinit var sut: TidepoolUploader

    @Before
    fun setUp() {
        whenever(authFlowOut.authState).thenReturn(authState)
        whenever(authFlowOut.authService).thenReturn(authService)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(config.APPLICATION_ID).thenReturn("info.nightscout.androidaps")
        sut = TidepoolUploader(
            aapsLogger, rxBus, context, preferences, uploadChunk, dateUtil,
            receiverDelegate, config, l, authFlowOut, RateLimit(dateUtil)
        )
    }

    private fun statusMessages(): List<String> {
        val captor = argumentCaptor<EventTidepoolStatus>()
        verify(rxBus, atLeastOnce()).send(captor.capture())
        return captor.allValues.map { it.status }
    }

    /** Getting a token is the first step of every login, so it shows that a login was started. */
    private fun verifyLoginStarted() =
        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN), any())

    private fun verifyNoLoginStarted() =
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN), any())

    /** Open a session without a service, so the uploader has one but makes no network call. */
    private fun startSessionWithUser(userId: String? = "user-1") {
        val session = Session(SESSION_TOKEN_HEADER, null)
        session.authReply = AuthReplyMessage().apply { userid = userId }
        sut.startSession(session, doUpload = false, from = "test")
    }

    /** Let AppAuth report a network problem instead of a token, so the login cannot finish. */
    private fun failTokenRefresh() {
        val networkError = AuthorizationException.fromTemplate(AuthorizationException.GeneralErrors.NETWORK_ERROR, IOException("no route to host"))
        doAnswer { invocation ->
            invocation.getArgument<AuthState.AuthStateAction>(1).execute(null, null, networkError)
            null
        }.whenever(authState).performActionWithFreshTokens(any<AuthorizationService>(), any<AuthState.AuthStateAction>())
    }

    /**
     * Open a session while the server already has a dataset: the "get open datasets" call answers with one,
     * which is what makes [TidepoolUploader.startSession] use it and run a waiting purge.
     */
    private fun startSessionWithOpenDatasetOnServer(): TidepoolApiService {
        val service: TidepoolApiService = mock()
        val listCall: Call<List<DatasetReplyMessage>> = mock()
        whenever(service.getOpenDataSets(any(), any(), any(), any())).thenReturn(listCall)
        doAnswer { invocation ->
            val reply = DatasetReplyMessage().apply { uploadId = "dataset-1" }
            invocation.getArgument<Callback<List<DatasetReplyMessage>>>(0).onResponse(listCall, Response.success(listOf(reply)))
            null
        }.whenever(listCall).enqueue(any())
        whenever(service.deleteDataSet(any(), any())).thenReturn(mock())

        val session = Session(SESSION_TOKEN_HEADER, service)
        session.authReply = AuthReplyMessage().apply { userid = "user-1" }
        session.token = "token-1"
        sut.startSession(session, doUpload = false, from = "test")
        return service
    }

    /**
     * Open a session with an already open dataset, so [TidepoolUploader.purge] can delete it right away.
     * The delete call answers with [answer], which runs on the calling thread.
     */
    private fun startSessionWithDataset(answer: (Call<DatasetReplyMessage>, Callback<DatasetReplyMessage>) -> Unit) {
        val service: TidepoolApiService = mock()
        val deleteCall: Call<DatasetReplyMessage> = mock()
        whenever(service.deleteDataSet(any(), any())).thenReturn(deleteCall)
        doAnswer { invocation ->
            answer(deleteCall, invocation.getArgument(0))
            null
        }.whenever(deleteCall).enqueue(any())

        val session = Session(SESSION_TOKEN_HEADER, service)
        session.authReply = AuthReplyMessage().apply { userid = "user-1" }
        session.token = "token-1"
        session.datasetReply = DatasetReplyMessage().apply { uploadId = "dataset-1" }
        sut.startSession(session, doUpload = false, from = "test")
    }

    @Test
    fun `resetInstance asks for a fresh login`() {
        sut.resetInstance()

        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `doLogin does nothing when the session is already there`() {
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)

        sut.doLogin(from = "test")

        verifyNoLoginStarted()
    }

    @Test
    fun `doLogin does nothing while a token is being fetched`() {
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)

        sut.doLogin(from = "test")

        // The state was set up by the test, the uploader must not start a second try
        verifyNoLoginStarted()
    }

    @Test
    fun `doLogin gets a token when there is no session`() {
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)

        sut.doLogin(from = "test")

        verifyLoginStarted()
    }

    @Test
    fun `upload is skipped when connectivity settings do not allow it`() {
        whenever(receiverDelegate.allowed).thenReturn(false)

        runBlocking { sut.doUpload("test") }

        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.BLOCKED), isNull())
        runBlocking { verify(uploadChunk, never()).getNext(anyOrNull()) }
    }

    @Test
    fun `upload without a session starts a login`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)

        runBlocking { sut.doUpload("test") }

        verifyLoginStarted()
        runBlocking { verify(uploadChunk, never()).getNext(anyOrNull()) }
    }

    @Test
    fun `session without a user id fails instead of uploading`() {
        whenever(receiverDelegate.allowed).thenReturn(true)

        startSessionWithUser(userId = null)

        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FAILED), any())
    }

    @Test
    fun `empty chunk is reported and nothing is sent`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(uploadChunk.getLastEnd()).thenReturn(now) // up to date, so no follow-up upload
        runBlocking { whenever(uploadChunk.getNext(anyOrNull())).thenReturn("[]") }
        startSessionWithUser()

        runBlocking { sut.doUpload("test") }

        assertThat(statusMessages()).contains("No data to upload")
        verify(uploadChunk, never()).setLastEnd(any())
    }

    @Test
    fun `purge is refused when connectivity settings do not allow it`() {
        whenever(receiverDelegate.allowed).thenReturn(false)

        sut.purge()

        assertThat(statusMessages()).contains("Purge blocked by connectivity settings")
        verifyNoLoginStarted()
    }

    @Test
    fun `purge without an open dataset connects first`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)

        sut.purge()

        assertThat(statusMessages()).contains("Purge: connecting…")
        verifyLoginStarted()
    }

    @Test
    fun `purge deletes the dataset and drops the session`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        // Tidepool answers a delete with 200 and an empty body
        startSessionWithDataset { call, callback -> callback.onResponse(call, Response.success<DatasetReplyMessage>(null)) }

        sut.purge()

        assertThat(statusMessages()).contains("All Tidepool data purged")
        // resetInstance(), so the next upload opens a fresh dataset
        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `purge keeps the session when the server refuses`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        startSessionWithDataset { call, callback ->
            callback.onResponse(call, Response.error(403, "no".toResponseBody("text/plain".toMediaTypeOrNull())))
        }

        sut.purge()

        assertThat(statusMessages().any { it.startsWith("Purge FAILED: 403") }).isTrue()
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `purge waits for the dataset and deletes it once the session is open`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)

        sut.purge() // no session yet, so the purge waits and a login is started

        val service = startSessionWithOpenDatasetOnServer()

        verify(service, timeout(2000)).deleteDataSet(any(), any())
    }

    @Test
    fun `purge is dropped when the login fails and does not run later`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)
        failTokenRefresh()

        sut.purge()

        assertThat(statusMessages()).contains("Purge failed - not connected")
        // A later upload opens a session, but the old purge must not delete anything now
        val service = startSessionWithOpenDatasetOnServer()
        verify(service, after(500).never()).deleteDataSet(any(), any())
    }

    @Test
    fun `purge reports a network failure`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        startSessionWithDataset { call, callback -> callback.onFailure(call, IOException("no route to host")) }

        sut.purge()

        assertThat(statusMessages().any { it.contains("no route to host") }).isTrue()
    }
}
