package app.aaps.implementation.maintenance.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Swapping an authorization code for tokens, and keeping the access token fresh.
 *
 * Both halves of the OAuth token endpoint live here because they differ by one form field and share
 * everything else - the parsing, the storing, and what each kind of failure means. Splitting them
 * produced two copies of that on the Android side.
 *
 * ## Why the failures are told apart
 *
 * A refresh that fails because the user revoked access needs a different answer than one that fails
 * because a train went into a tunnel: the first has to send the user back through a sign in, and the
 * second should be tried again. Google says which by returning `invalid_grant`, and that is the only
 * reason [TokenFailure] has more than one case.
 */
class GoogleTokenClient(
    private val http: HttpClient,
    private val tokens: GoogleTokenStore,
    private val clientId: String,
    private val redirectUri: String,
    private val tokenEndpoint: String = TOKEN_ENDPOINT,
    private val now: () -> Long
) {

    /**
     * Finishes a sign in.
     *
     * Refuses a response with no refresh token rather than storing the access token alone. Without a
     * refresh token the sign in works for an hour and then fails for good, which looks to the user
     * like the feature breaking by itself days later.
     */
    suspend fun exchangeCode(authCode: String): TokenFailure? {
        val verifier = tokens.codeVerifier ?: return TokenFailure.Failed("no code verifier, the sign in was not started here")

        val response = runCatching {
            http.submitForm(
                url = tokenEndpoint,
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("code", authCode)
                    append("code_verifier", verifier)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirectUri)
                }
            )
        }.getOrElse { return TokenFailure.Failed(it.message ?: "could not reach Google") }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.isSuccess()) return failureFor(body, response.status.value)

        val parsed = parse(body) ?: return TokenFailure.Failed("Google's answer could not be read")
        val refresh = parsed.refreshToken
            ?: return TokenFailure.Failed("Google returned no refresh token, so the sign in would stop working within the hour")

        tokens.refreshToken = refresh
        tokens.accessToken = parsed.accessToken
        tokens.expiresAt = now() + parsed.expiresInSeconds * 1000
        tokens.codeVerifier = null
        tokens.state = null
        return null
    }

    /**
     * An access token that is good now, refreshing first if it is not.
     *
     * Returns the stored one untouched when it is still good, so ordinary use does not talk to Google
     * at all.
     */
    suspend fun validAccessToken(): Result<String> {
        tokens.accessToken?.let { existing ->
            if (tokens.accessTokenUsableAt(now())) return Result.success(existing)
        }
        val refresh = tokens.refreshToken ?: return Result.failure(TokenException(TokenFailure.NotSignedIn))

        val response = runCatching {
            http.submitForm(
                url = tokenEndpoint,
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("grant_type", "refresh_token")
                    append("refresh_token", refresh)
                }
            )
        }.getOrElse { return Result.failure(TokenException(TokenFailure.Failed(it.message ?: "could not reach Google"))) }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.isSuccess()) {
            val failure = failureFor(body, response.status.value)
            // A refusal of the refresh token itself is final: nothing is left to try with, so the
            // stored sign in is cleared rather than left to fail the same way on every screen.
            if (failure is TokenFailure.SignInExpired) tokens.clear()
            return Result.failure(TokenException(failure))
        }

        val parsed = parse(body) ?: return Result.failure(TokenException(TokenFailure.Failed("Google's answer could not be read")))
        val access = parsed.accessToken ?: return Result.failure(TokenException(TokenFailure.Failed("Google returned no access token")))

        tokens.accessToken = access
        tokens.expiresAt = now() + parsed.expiresInSeconds * 1000
        // Google usually does not return a new refresh token here, and when it does it replaces the old.
        parsed.refreshToken?.let { tokens.refreshToken = it }
        return Result.success(access)
    }

    /**
     * `invalid_grant` is Google saying the refresh token is finished - revoked, or the account's
     * password changed. Everything else may be temporary.
     */
    private fun failureFor(body: String, status: Int): TokenFailure {
        val error = runCatching { Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        return if (error == "invalid_grant") TokenFailure.SignInExpired
        else TokenFailure.Failed("Google refused the request ($status${error?.let { ": $it" } ?: ""})")
    }

    private fun parse(body: String): TokenResponse? = runCatching {
        val json = Json.parseToJsonElement(body).jsonObject
        TokenResponse(
            accessToken = json["access_token"]?.jsonPrimitive?.content?.ifEmpty { null },
            refreshToken = json["refresh_token"]?.jsonPrimitive?.content?.ifEmpty { null },
            expiresInSeconds = json["expires_in"]?.jsonPrimitive?.longOrNull ?: DEFAULT_EXPIRY_SECONDS
        )
    }.getOrNull()

    private data class TokenResponse(val accessToken: String?, val refreshToken: String?, val expiresInSeconds: Long)

    companion object {

        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

        /** What Google returns in practice, used when the answer leaves it out. */
        private const val DEFAULT_EXPIRY_SECONDS = 3600L
    }
}

/** Carries a [TokenFailure] through a [Result], so the reason survives as far as the user. */
class TokenException(val failure: TokenFailure) : Exception(failure.toString())
