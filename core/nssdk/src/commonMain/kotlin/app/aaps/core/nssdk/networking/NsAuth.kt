package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.exceptions.DateHeaderOutOfToleranceException
import app.aaps.core.nssdk.exceptions.InvalidAccessTokenException
import app.aaps.core.nssdk.networking.NsAuth.REFRESHABLE
import app.aaps.core.nssdk.networking.Status.MESSAGE_DATE_HEADER_OUT_OF_TOLERANCE
import app.aaps.core.nssdk.nsSdkJson
import app.aaps.core.nssdk.remotemodel.RemoteAuthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlin.time.Clock

/**
 * The Nightscout token dance, as an `HttpSend` interceptor.
 *
 * This is written by hand rather than with Ktor's `Auth` / `bearer` provider, because that provider
 * differs in ways that lose data without saying so:
 *
 * - it **omits** the header when it has no token. Nightscout does not reject an unauthenticated
 *   request - it answers **200 with the anonymous role**, which flows into `hasWritePermission` and
 *   makes `DataSyncWorker` skip the whole upload block. Nothing is logged. Here the header is always
 *   sent, even while the token is still empty.
 * - it refreshes on 401 only. This refreshes on 401 and 403, because Nightscout answers 403 for a
 *   deleted token - see [REFRESHABLE].
 * - its refresh hook never sees the response body, so the clock-skew case below could not exist.
 *
 * `NsSdkAuthContractTest` pins all of it.
 */
internal object NsAuth {

    fun install(client: HttpClient, refreshClient: HttpClient, baseUrl: String, refreshToken: String) {
        // The current Bearer token. Deliberately unsynchronised, matching the Retrofit interceptor:
        // concurrent 401s each refresh. Serialising them is an improvement, but one to make on
        // purpose with its own test rather than as a side effect of changing transport.
        var jwtToken = ""

        client.plugin(HttpSend).intercept { request ->
            request.headers[HttpHeaders.Date] = nowMillis().toString()
            request.headers[HttpHeaders.Authorization] = "Bearer $jwtToken"

            // save() so the body can be read here AND again by the caller - Ktor streams it once.
            val firstCall = execute(request).save()
            if (firstCall.response.status.value !in REFRESHABLE) return@intercept firstCall

            // Checked BEFORE refreshing: a clock that is too far off is not fixed by a new token, and
            // the caller needs to see this specific error to tell the user to fix the time.
            if (firstCall.response.bodyAsText().contains(MESSAGE_DATE_HEADER_OUT_OF_TOLERANCE))
                throw DateHeaderOutOfToleranceException("Data header out of tolerance")

            val refreshed = refresh(refreshClient, baseUrl, refreshToken) ?: return@intercept firstCall
            jwtToken = refreshed

            // Rebuilt rather than replayed, so Date is recomputed along with the new token. Exactly
            // one retry - never a loop.
            request.headers[HttpHeaders.Date] = nowMillis().toString()
            request.headers[HttpHeaders.Authorization] = "Bearer $jwtToken"
            execute(request)
        }
    }

    /**
     * Asks for a new access token.
     *
     * Returns the token on success, or null to mean "carry on with the original failed response" -
     * which is what the Retrofit version did for any refresh answer that was neither 200 nor a
     * 401/403.
     */
    private suspend fun refresh(refreshClient: HttpClient, baseUrl: String, refreshToken: String): String? {
        val response = refreshClient.get(refreshUrl(baseUrl, refreshToken))
        return when {
            response.status.value in REFRESHABLE ->
                throw InvalidAccessTokenException("Invalid access token")

            response.status.value != 200         -> null
            else                                 ->
                runCatching {
                    nsSdkJson.decodeFromString(RemoteAuthResponse.serializer(), response.bodyAsText()).token
                }.getOrNull()
        }
    }

    /**
     * `https://<host>/api/v2/authorization/request/<refreshToken>`, built from the **host only**.
     *
     * The Retrofit annotation began with a slash (`/api/v2/...`), which meant it was resolved against
     * the host root and any sub-path was dropped. That is reproduced here on purpose: whether a
     * sub-path install should keep its sub-path when refreshing is a Nightscout question, and this
     * change is about transport, not about answering it.
     */
    private fun refreshUrl(baseUrl: String, refreshToken: String): String =
        URLBuilder().apply {
            takeFrom(baseUrl)
            encodedPathSegments = emptyList()
            appendPathSegments(listOf("api", "v2", "authorization", "request", refreshToken), encodeSlash = true)
        }.buildString()

    // kotlin.time.Clock rather than System.currentTimeMillis(), which is JVM only. kotlinx-datetime
    // is already a dependency of this module.
    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * What is treated as "worth trying a new token for".
     *
     * **The 403 is needed.** Checked in the server source and against a live Nightscout (2026-10-21):
     * - a broken or expired login (JWT): 401;
     * - a login whose access token lacks the permission for the call: 403;
     * - a login whose access token was **deleted** in Nightscout: also 403, not 401. API v3 still
     *   accepts the signed login, finds no token behind it, and so finds no permission. The login
     *   itself stays valid for up to 8 hours.
     *
     * Only the refresh tells the last two apart: asked for a new login for a deleted token,
     * Nightscout answers 401, which becomes [InvalidAccessTokenException]. Without 403 here, a phone
     * whose token was deleted (for example a client removed on the master) would see "no permission"
     * for up to 8 hours instead of "invalid token".
     *
     * The cost is one refresh and one retry for each call that really lacks the permission. It is
     * **not** the Drive bug: nothing here clears a credential, so no user is signed out by it.
     */
    private val REFRESHABLE = listOf(401, 403)
}
