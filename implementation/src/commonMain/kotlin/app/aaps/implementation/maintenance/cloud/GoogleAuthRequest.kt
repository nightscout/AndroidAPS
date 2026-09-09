package app.aaps.implementation.maintenance.cloud

import app.aaps.core.objects.crypto.CryptoPrimitives
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Building the Google sign in, and the PKCE that protects it.
 *
 * Everything here is arithmetic on strings, which is why it is worth having on its own: the sign in
 * itself cannot be tested without a browser and a Google account, but the values handed to it can be
 * checked exactly, and one of them has a published answer.
 *
 * ## What PKCE is doing
 *
 * The app sends a challenge when it asks for a sign in, and the matching verifier when it swaps the
 * code for a token. Only the app that made the challenge knows the verifier, so a code stolen out of
 * the redirect is useless on its own. This matters more here than in a web app: the redirect goes to
 * `localhost`, which any other app on the machine could in principle listen on if it got there first.
 *
 * The challenge is `base64url(sha256(verifier))` with the padding removed - `S256` in the protocol.
 * The plain `S256`-less variant, where challenge and verifier are the same string, is not offered
 * here at all; it protects nothing and exists only for clients that cannot hash.
 */
@OptIn(ExperimentalEncodingApi::class)
class GoogleAuthRequest(private val crypto: CryptoPrimitives) {

    /**
     * A fresh verifier: 32 random bytes as base64url, which is 43 characters.
     *
     * RFC 7636 allows 43 to 128 characters. The low end is the recommended length and is what the
     * Android side already produces, so the two stay comparable.
     */
    fun newVerifier(): String = base64Url(crypto.randomBytes(VERIFIER_BYTES))

    /** The `S256` challenge for [verifier]. */
    fun challengeFor(verifier: String): String = base64Url(crypto.sha256Bytes(verifier.encodeToByteArray()))

    /** An opaque value echoed back in the redirect, so a redirect nobody asked for can be spotted. */
    fun newState(): String = base64Url(crypto.randomBytes(STATE_BYTES))

    /**
     * The URL the browser is sent to.
     *
     * `access_type=offline` with `prompt=consent` is what makes Google return a refresh token;
     * without both, a sign in works once and then quietly stops when the access token expires.
     */
    fun authorizationUrl(
        authEndpoint: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        challenge: String,
        state: String
    ): String {
        val query = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to scope,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "consent"
        ).joinToString("&") { (name, value) -> "$name=${formEncode(value)}" }
        return "$authEndpoint?$query"
    }

    /**
     * Percent encoding for a query value.
     *
     * Deliberately encodes everything outside the unreserved set of RFC 3986, including `/`, `:` and
     * `+`. A scope list and a redirect URI both carry those, and Google reads a raw `+` as a space.
     */
    fun formEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (char.isUnreserved()) append(char)
            else append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
    }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '.' || this == '_' || this == '~'

    /** base64url without padding, which is what the protocol asks for in both places it is used. */
    private fun base64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    private companion object {

        private const val VERIFIER_BYTES = 32
        private const val STATE_BYTES = 16
        private const val HEX = "0123456789ABCDEF"
    }
}
