package app.aaps.plugins.sync.tidepool.comm

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
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
    private val tidepoolRepository = TidepoolRepository(aapsLogger, rxBus)
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences: Preferences = mock()
    private val uploadChunk: UploadChunk = mock()
    private val dateUtil: DateUtil = mock()
    private val receiverDelegate: TidepoolReceiverDelegate = mock()
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
        whenever(config.VERSION_NAME).thenReturn("4.0.0") // client.version of a new dataset
        sut = TidepoolUploader(
            aapsLogger, tidepoolRepository, context, preferences, uploadChunk, dateUtil,
            receiverDelegate, config, l, authFlowOut, RateLimit(dateUtil)
        )
    }

    /** What the Tidepool screen shows in its log */
    private fun statusMessages(): List<String> = tidepoolRepository.logList.value.map { it.status }

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
     * A fake Tidepool server for the dataset calls. It holds `dataSets` as upload id to device id, newest
     * first. Like the real server, a list filters by `deviceId` (when given) and returns at most `size`;
     * opening adds a new [TidepoolUploader.DEVICE_NAME] dataset; a successful delete removes the dataset.
     * Every call answers at once on the calling thread.
     */
    private class FakeTidepool(vararg initialDataSets: Pair<String, String?>) {

        val dataSets = mutableListOf(*initialDataSets)
        val deleted = mutableListOf<String>()
        val service: TidepoolApiService = mock()

        /** Uploads received so far, and the number (1, 2, …) of the one that answers with an error. */
        @Volatile var uploads = 0
        var failUpload: Int? = null

        /** How a delete answers. The default is what Tidepool does: 200 with an empty body. */
        var deleteAnswer: (Call<DatasetReplyMessage>, Callback<DatasetReplyMessage>) -> Unit =
            { call, callback -> callback.onResponse(call, Response.success<DatasetReplyMessage>(null)) }

        init {
            whenever(service.getDataSets(any(), any(), any(), anyOrNull(), any())).thenAnswer { list ->
                val deviceId = list.getArgument<String?>(3)
                val replies = dataSets
                    .filter { deviceId == null || it.second == deviceId }
                    .take(list.getArgument<Int>(4))
                    .map { DatasetReplyMessage().apply { uploadId = it.first } }
                answerWith<List<DatasetReplyMessage>> { call, callback -> callback.onResponse(call, Response.success(replies)) }
            }
            whenever(service.doUpload(any(), any(), any())).thenAnswer {
                val number = ++uploads
                answerWith<UploadReplyMessage> { call, callback ->
                    if (number == failUpload) callback.onResponse(call, Response.error(500, "down".toResponseBody("text/plain".toMediaTypeOrNull())))
                    else callback.onResponse(call, Response.success(UploadReplyMessage()))
                }
            }
            whenever(service.openDataSet(any(), any(), any())).thenAnswer {
                dataSets.add(0, "new-1" to TidepoolUploader.DEVICE_NAME)
                val reply = DatasetReplyMessage().apply { uploadId = "new-1" }
                answerWith<DatasetReplyMessage> { call, callback -> callback.onResponse(call, Response.success(reply)) }
            }
            whenever(service.deleteDataSet(any(), any())).thenAnswer { delete ->
                val id = delete.getArgument<String>(1)
                answerWith<DatasetReplyMessage> { call, callback ->
                    deleteAnswer(call, object : Callback<DatasetReplyMessage> {
                        override fun onResponse(call: Call<DatasetReplyMessage>, response: Response<DatasetReplyMessage>) {
                            if (response.isSuccessful) {
                                dataSets.removeAll { it.first == id }
                                deleted.add(id)
                            }
                            callback.onResponse(call, response)
                        }

                        override fun onFailure(call: Call<DatasetReplyMessage>, t: Throwable) = callback.onFailure(call, t)
                    })
                }
            }
        }

        /** A call whose enqueue() runs [answer] at once. */
        private fun <T> answerWith(answer: (Call<T>, Callback<T>) -> Unit): Call<T> {
            val call: Call<T> = mock()
            doAnswer { invocation ->
                answer(call, invocation.getArgument(0))
                null
            }.whenever(call).enqueue(any())
            return call
        }
    }

    /**
     * Let AppAuth answer every token request at once. [tokenFor] gets the number of the request (1, 2, …)
     * and returns the token, or null for a failed refresh.
     */
    private fun answerTokens(tokenFor: (Int) -> String? = { "token-1" }) {
        var requests = 0
        doAnswer { invocation ->
            val token = tokenFor(++requests)
            val error = if (token == null) AuthorizationException.fromTemplate(AuthorizationException.GeneralErrors.NETWORK_ERROR, IOException("no route to host")) else null
            invocation.getArgument<AuthState.AuthStateAction>(1).execute(token, null, error)
            null
        }.whenever(authState).performActionWithFreshTokens(any<AuthorizationService>(), any<AuthState.AuthStateAction>())
    }

    /**
     * Open a session on [server]. The session finds or opens its dataset in the background. AppAuth gives a
     * valid token from now on, unless the test sets up other answers after this.
     */
    private fun startSession(server: FakeTidepool): Session {
        answerTokens()
        val session = Session(SESSION_TOKEN_HEADER, server.service)
        session.authReply = AuthReplyMessage().apply { userid = "user-1" }
        session.token = "token-1"
        sut.startSession(session, doUpload = false, from = "test")
        return session
    }

    /** Open a session on [server] and wait until it has its dataset, so a purge can start right away. */
    private fun startSessionAndWait(server: FakeTidepool): Session {
        val session = startSession(server)
        verify(authFlowOut, timeout(2000)).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED), any())
        return session
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
        runBlocking { whenever(uploadChunk.getNext(anyOrNull())).thenReturn(emptyList()) }
        startSessionWithUser()

        runBlocking { sut.doUpload("test") }

        assertThat(statusMessages()).contains("No data to upload")
        verify(uploadChunk, never()).setLastEnd(any())
    }

    @Test
    fun `upload asks for a valid token and does not reuse the one from the session start`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(uploadChunk.getLastEnd()).thenReturn(now) // up to date, so no follow-up upload
        runBlocking { whenever(uploadChunk.getNext(anyOrNull())).thenReturn(listOf("""[{"type":"cbg"}]""")) }
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        val session = startSessionAndWait(server) // the session started with "token-1"
        answerTokens { "fresh-$it" } // and AppAuth has refreshed it since

        runBlocking { sut.doUpload("test") }

        verify(server.service).doUpload(eq("fresh-1"), eq("aaps-1"), any())
        assertThat(session.token).isEqualTo("fresh-1")
        verify(uploadChunk, timeout(2000)).setLastEnd(any())
    }

    @Test
    fun `upload without a valid token fails and frees the upload lock`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(uploadChunk.getLastEnd()).thenReturn(now)
        runBlocking { whenever(uploadChunk.getNext(anyOrNull())).thenReturn(listOf("""[{"type":"cbg"}]""")) }
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        startSessionAndWait(server)
        answerTokens { null }

        runBlocking { sut.doUpload("test") }

        verify(server.service, never()).doUpload(any(), any(), any())
        verify(authFlowOut).updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Upload FAILED - no valid token")
        // The lock is free again: the next upload is not skipped as "already in progress"
        answerTokens { "fresh-$it" }
        runBlocking { sut.doUpload("test") }
        verify(server.service).doUpload(eq("fresh-1"), eq("aaps-1"), any())
    }

    @Test
    fun `a window larger than one batch is sent in parts and LastEnd moves once at the end`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(uploadChunk.getLastEnd()).thenReturn(now)
        runBlocking { whenever(uploadChunk.getNext(anyOrNull())).thenReturn(listOf("[1]", "[2]", "[3]")) }
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        startSessionAndWait(server)

        runBlocking { sut.doUpload("test") }

        verify(uploadChunk, timeout(2000)).setLastEnd(any())
        assertThat(server.uploads).isEqualTo(3)
        assertThat(statusMessages()).containsAtLeast("Uploading part 1 of 3", "Uploading part 2 of 3", "Uploading part 3 of 3")
    }

    @Test
    fun `a failed part keeps LastEnd so the window is sent again, and frees the upload lock`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(uploadChunk.getLastEnd()).thenReturn(now)
        runBlocking { whenever(uploadChunk.getNext(anyOrNull())).thenReturn(listOf("[1]", "[2]", "[3]")) }
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        server.failUpload = 2
        startSessionAndWait(server)

        runBlocking { sut.doUpload("test") }

        verify(authFlowOut, timeout(2000)).updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Upload FAILED")
        assertThat(server.uploads).isEqualTo(2) // part 3 is not sent after part 2 failed
        verify(uploadChunk, never()).setLastEnd(any())
        // The lock is free again: the next trigger reads a new window instead of being skipped
        runBlocking { sut.doUpload("test") }
        runBlocking { verify(uploadChunk, timeout(2000).times(2)).getNext(anyOrNull()) }
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
    fun `session reuses only a dataset with the AAPS device id`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)

        startSessionAndWait(server)

        verify(server.service).getDataSets(any(), eq("user-1"), eq("info.nightscout.androidaps"), eq("AAPS"), eq(1))
        verify(server.service, never()).openDataSet(any(), any(), any())
    }

    @Test
    fun `session opens a new dataset when only a dataset from before 4_0 exists`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        // Before 4.0 the dataset was named "Tandem" and had no device id
        val server = FakeTidepool("tandem-1" to null)

        startSessionAndWait(server)

        verify(server.service).openDataSet(any(), any(), any())
        assertThat(server.dataSets.map { it.first }).containsExactly("new-1", "tandem-1").inOrder()
    }

    @Test
    fun `purge deletes the dataset and drops the session`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        startSessionAndWait(server)

        sut.purge()

        assertThat(server.deleted).containsExactly("aaps-1")
        assertThat(statusMessages()).contains("All Tidepool data purged (deleted datasets: 1)")
        // resetInstance(), so the next upload opens a fresh dataset
        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `purge deletes older datasets too and the one in use last`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME, "tandem-2" to null, "tandem-1" to null)
        startSessionAndWait(server)

        sut.purge()

        assertThat(server.deleted).containsExactly("tandem-2", "tandem-1", "aaps-1").inOrder()
        assertThat(server.dataSets).isEmpty()
        assertThat(statusMessages()).contains("All Tidepool data purged (deleted datasets: 3)")
    }

    @Test
    fun `purge deletes more datasets than fit in one list`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        // Old versions opened a new dataset on every session, so there can be many
        val old = (1..TidepoolUploader.PURGE_PAGE_SIZE + 20).map { "old-$it" to null }
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME, *old.toTypedArray())
        startSessionAndWait(server)

        sut.purge()

        assertThat(server.dataSets).isEmpty()
        assertThat(server.deleted.last()).isEqualTo("aaps-1")
        // A long purge shows progress, then the total
        val messages = statusMessages()
        assertThat(messages.filter { it.startsWith("Purge: ") }).containsExactly("Purge: 100 datasets deleted")
        assertThat(messages).contains("All Tidepool data purged (deleted datasets: ${TidepoolUploader.PURGE_PAGE_SIZE + 21})")
    }

    @Test
    fun `purge asks for a valid token before every call`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME, "tandem-1" to null)
        val session = startSessionAndWait(server)
        // A long purge outlives the access token, so AppAuth hands out a new one on the way
        answerTokens { "fresh-$it" }

        sut.purge()

        verify(server.service).getDataSets(eq("fresh-1"), any(), any(), isNull(), any())
        verify(server.service).deleteDataSet("fresh-2", "tandem-1")
        verify(server.service).getDataSets(eq("fresh-3"), any(), any(), isNull(), any())
        verify(server.service).deleteDataSet("fresh-4", "aaps-1")
        assertThat(session.token).isEqualTo("fresh-4")
        verify(authFlowOut, atLeastOnce()).saveAuthState()
        assertThat(statusMessages()).contains("All Tidepool data purged (deleted datasets: 2)")
    }

    @Test
    fun `purge stops when the token cannot be refreshed and keeps the dataset in use`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME, "tandem-1" to null)
        startSessionAndWait(server)
        answerTokens { if (it == 1) "fresh-1" else null } // the list works, then the refresh fails

        sut.purge()

        verify(server.service, never()).deleteDataSet(any(), any())
        assertThat(server.dataSets).hasSize(2)
        assertThat(statusMessages()).contains("Purge failed - not connected")
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `purge keeps the session when the server refuses`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        server.deleteAnswer = { call, callback ->
            callback.onResponse(call, Response.error(403, "no".toResponseBody("text/plain".toMediaTypeOrNull())))
        }
        startSessionAndWait(server)

        sut.purge()

        assertThat(statusMessages().any { it.startsWith("Purge FAILED: 403") }).isTrue()
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `purge stops at the first failure and keeps the dataset in use`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME, "tandem-1" to null)
        server.deleteAnswer = { call, callback -> callback.onFailure(call, IOException("no route to host")) }
        startSessionAndWait(server)

        sut.purge()

        verify(server.service, never()).deleteDataSet(any(), eq("aaps-1"))
        assertThat(server.dataSets).hasSize(2)
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
    }

    @Test
    fun `purge waits for the dataset and deletes it once the session is open`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)

        sut.purge() // no session yet, so the purge waits and a login is started
        startSession(server)

        verify(server.service, timeout(2000)).deleteDataSet(any(), eq("aaps-1"))
    }

    @Test
    fun `purge also deletes old datasets when the session had to open a new one`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)
        // Only a dataset from before 4.0, so the session opens a new one first
        val server = FakeTidepool("tandem-1" to null)

        sut.purge()
        startSession(server)

        // resetInstance() runs after the last delete has finished
        verify(authFlowOut, timeout(2000)).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NONE), isNull())
        assertThat(server.deleted).containsExactly("tandem-1", "new-1").inOrder()
    }

    @Test
    fun `purge is dropped when the login fails and does not run later`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NO_SESSION)
        failTokenRefresh()

        sut.purge()

        assertThat(statusMessages()).contains("Purge failed - not connected")
        // A later upload opens a session, but the old purge must not delete anything now
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        startSession(server)
        verify(server.service, after(500).never()).deleteDataSet(any(), any())
    }

    @Test
    fun `purge reports a network failure`() {
        whenever(receiverDelegate.allowed).thenReturn(true)
        val server = FakeTidepool("aaps-1" to TidepoolUploader.DEVICE_NAME)
        server.deleteAnswer = { call, callback -> callback.onFailure(call, IOException("no route to host")) }
        startSessionAndWait(server)

        sut.purge()

        assertThat(statusMessages().any { it.contains("no route to host") }).isTrue()
    }
}
