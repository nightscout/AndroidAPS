package app.aaps.plugins.sync.tidepool.auth

import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

/**
 *
 * Handle aaps://callback/tidepool
 */
class AuthFlowIn : DaggerAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var authFlowOut: AuthFlowOut
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var tidepoolUploader: TidepoolUploader
    @Inject lateinit var rxBus: RxBus

    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    public override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)
        aapsLogger.debug(LTag.TIDEPOOL, "Auth callback received")
        processResponse(intent)
        finish()
    }

    private fun processResponse(intent: Intent) {
        //aapsLogger.debug(LTag.TIDEPOOL, "processResponse")
        val authorizationResponse = AuthorizationResponse.fromIntent(intent)
        val authorizationException = AuthorizationException.fromIntent(intent)

        if (authorizationResponse == null && authorizationException == null) {
            aapsLogger.error(LTag.TIDEPOOL, "Authentication not received")
            return
        }
        authFlowOut.authState.update(authorizationResponse, authorizationException)
        if (authorizationException != null) {
            aapsLogger.error("Got authorization error - resetting state: $authorizationException")
            authFlowOut.eraseAuthState(authorizationException.message)
        }
        if (authorizationResponse != null) {
            // authorization completed
            authFlowOut.saveAuthState()

            authFlowOut.authService.performTokenRequest(authorizationResponse.createTokenExchangeRequest()) { tokenResponse, exception ->
                coroutineScope.launch {
                    authFlowOut.authState.update(tokenResponse, exception)
                    if (exception != null) {
                        aapsLogger.error(LTag.TIDEPOOL, "Token request exception: $exception")
                        authFlowOut.eraseAuthState(exception.message)
                    }
                    if (tokenResponse != null) {
                        aapsLogger.debug(LTag.TIDEPOOL, "Got first token")
                        authFlowOut.saveAuthState()

                        val configuration = authFlowOut.authState.getAuthorizationServiceConfiguration()
                        if (configuration == null) {
                            aapsLogger.error(LTag.TIDEPOOL, "Got null for authorization service configuration")
                            return@launch
                        }
                        val discoveryDoc = configuration.discoveryDoc
                        if (discoveryDoc == null) {
                            aapsLogger.error(LTag.TIDEPOOL, "Got null for discoveryDoc")
                            return@launch
                        }
                        val userInfoEndpoint = discoveryDoc.userinfoEndpoint
                        if (userInfoEndpoint == null) {
                            aapsLogger.error(LTag.TIDEPOOL, "Got null for userInfoEndpoint")
                            return@launch
                        }

                        try {
                            val conn = AppAuthConfiguration.DEFAULT.connectionBuilder.openConnection(userInfoEndpoint)
                            conn.setRequestProperty("Authorization", "Bearer " + tokenResponse.accessToken)
                            conn.setInstanceFollowRedirects(false)
                            val response = BufferedReader(InputStreamReader(conn.getInputStream())).readText()
                            aapsLogger.debug(LTag.TIDEPOOL, "UserInfo: $response")

                            authFlowOut.authState.performActionWithFreshTokens(authFlowOut.authService) { accessToken, idToken, authorizationException ->
                                coroutineScope.launch {
                                    if (authorizationException != null) {
                                        aapsLogger.error(LTag.TIDEPOOL, "Got fresh token exception: $authorizationException")
                                        return@launch
                                    }
                                    val session = tidepoolUploader.createSession()
                                    session.authReply = AuthReplyMessage()
                                    session.token = accessToken
                                    val userInfo = JSONObject(response)
                                    val userId = userInfo.optString("sub")
                                    if (userId.isNotEmpty()) {
                                        session.authReply?.userid = userId
                                        preferences.put(TidepoolStringNonKey.SubscriptionId, userId)
                                        tidepoolUploader.startSession(session, from = "AuthFlowIn::processResponse")
                                    } else {
                                        aapsLogger.error(LTag.TIDEPOOL, "Could not get 'sub' field - cannot proceed")
                                        authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Cannot read sub name")
                                        authFlowOut.eraseAuthState("missing sub")
                                    }
                                }
                            }
                        } catch (exception: Exception) {
                            rxBus.send(EventTidepoolStatus(exception.toString()))
                        }
                    }
                }
            }
        } else {
            rxBus.send(EventTidepoolStatus((authorizationException.toString())))
        }
    }
}