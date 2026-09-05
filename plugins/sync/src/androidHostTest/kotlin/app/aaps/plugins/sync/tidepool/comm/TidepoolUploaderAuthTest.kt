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
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for the token refresh in [TidepoolUploader.handleTokenLoginAndStartSession].
 *
 * A refresh that fails only because of the network must keep the saved login and try again later. Before
 * the fix for https://github.com/nightscout/AndroidAPS/issues/4989 every failure opened the Tidepool login
 * page in the browser, so users on a weak connection were asked to log in again and again.
 *
 * Runs under Robolectric because [AuthorizationException] is logged as text, which uses org.json.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [35])
class TidepoolUploaderAuthTest {

    private val aapsLogger: AAPSLogger = mock()
    private val rxBus: RxBus = mock()
    private val context: Context = mock()
    private val preferences: Preferences = mock()
    private val uploadChunk: UploadChunk = mock()
    private val dateUtil: DateUtil = mock()
    private val receiverDelegate: ReceiverDelegate = mock()
    private val config: Config = mock()
    private val l: L = mock()
    private val authFlowOut: AuthFlowOut = mock()
    private val authState: AuthState = mock()
    private val authService: AuthorizationService = mock()

    private lateinit var sut: TidepoolUploader

    @Before
    fun setUp() {
        whenever(authFlowOut.authState).thenReturn(authState)
        whenever(authFlowOut.authService).thenReturn(authService)
        // Fixed time, so the two calls in the rate limit test fall into the same window
        whenever(dateUtil.now()).thenReturn(1_000_000L)
        sut = TidepoolUploader(
            aapsLogger, rxBus, context, preferences, uploadChunk, dateUtil,
            receiverDelegate, config, l, authFlowOut, RateLimit(dateUtil)
        )
    }

    /** Let AppAuth report [ex] and no access token to the callback of performActionWithFreshTokens. */
    private fun failTokenRefreshWith(ex: AuthorizationException) {
        doAnswer { invocation ->
            invocation.getArgument<AuthState.AuthStateAction>(1).execute(null, null, ex)
            null
        }.whenever(authState).performActionWithFreshTokens(any<AuthorizationService>(), any<AuthState.AuthStateAction>())
    }

    @Test
    fun `network problem keeps the login and does not open the browser`() {
        failTokenRefreshWith(AuthorizationException.fromTemplate(AuthorizationException.GeneralErrors.NETWORK_ERROR, IOException("no route to host")))

        sut.handleTokenLoginAndStartSession(doUpload = false, from = "test")

        verify(authFlowOut, never()).doTidePoolInitialLogin(any())
        // NO_SESSION, so the next upload tries the silent refresh again
        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NO_SESSION), any())
    }

    @Test
    fun `refused credentials open the login page`() {
        failTokenRefreshWith(AuthorizationException.TokenRequestErrors.INVALID_GRANT)

        sut.handleTokenLoginAndStartSession(doUpload = false, from = "test")

        verify(authFlowOut).doTidePoolInitialLogin(any())
        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN), any())
    }

    @Test
    fun `login page is not opened twice in a short time`() {
        failTokenRefreshWith(AuthorizationException.TokenRequestErrors.INVALID_GRANT)

        sut.handleTokenLoginAndStartSession(doUpload = false, from = "first")
        sut.handleTokenLoginAndStartSession(doUpload = false, from = "second")

        verify(authFlowOut, times(1)).doTidePoolInitialLogin(any())
    }
}
