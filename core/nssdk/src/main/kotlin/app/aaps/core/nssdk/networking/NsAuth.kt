package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.exceptions.DateHeaderOutOfToleranceException
import app.aaps.core.nssdk.exceptions.InvalidAccessTokenException
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
 * - it refreshes on 401 only; Nightscout also answers **403**.
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

    private fun nowMillis(): Long = System.currentTimeMillis()

    /** Nightscout answers 403 as well as 401 for an expired token. */
    private val REFRESHABLE = listOf(401, 403)
}
