package app.aaps.implementation.maintenance.cloud

import app.aaps.core.objects.crypto.platformCryptoPrimitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The values a Google sign in is built from.
 *
 * The one that matters is the PKCE challenge, and it has a published answer: RFC 7636 prints a
 * verifier and the challenge it must produce. Getting this wrong does not fail loudly - Google
 * simply refuses the token swap at the very end, with an error that describes none of this - so it
 * is worth pinning against the specification rather than against ourselves.
 *
 * Runs on every platform, because the challenge is computed from [platformCryptoPrimitives] and a
 * digest that differed between platforms would mean a sign in that works on one and not the other.
 */
class GoogleAuthRequestTest {

    private val sut = GoogleAuthRequest(platformCryptoPrimitives())

    /**
     * RFC 7636 appendix B, the worked example for `S256`. If this fails, no sign in completes.
     */
    @Test
    fun `the code challenge matches the one in the specification`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", sut.challengeFor(verifier))
    }

    /** base64url, not base64: a `+` or `/` in a challenge is rejected, and padding is not allowed. */
    @Test
    fun `the challenge is base64url with no padding`() {
        val challenge = sut.challengeFor(sut.newVerifier())

        assertTrue(challenge.none { it == '+' || it == '/' || it == '=' }, challenge)
    }

    /** RFC 7636 allows 43 to 128 characters. 32 random bytes give the recommended 43. */
    @Test
    fun `a verifier is the length the specification allows`() {
        val verifier = sut.newVerifier()

        assertEquals(43, verifier.length, verifier)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' }, verifier)
    }

    /** Two sign ins sharing a verifier would undo the point of having one. */
    @Test
    fun `every verifier and state is new`() {
        assertNotEquals(sut.newVerifier(), sut.newVerifier())
        assertNotEquals(sut.newState(), sut.newState())
    }

    @Test
    fun `the same verifier always gives the same challenge`() {
        val verifier = sut.newVerifier()

        assertEquals(sut.challengeFor(verifier), sut.challengeFor(verifier))
    }

    @Test
    fun `the authorization url carries everything Google needs`() {
        val url = sut.authorizationUrl(
            authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            clientId = "the-client",
            redirectUri = "http://localhost:8080/oauth/callback",
            scope = "https://www.googleapis.com/auth/drive.file",
            challenge = "the-challenge",
            state = "the-state"
        )

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"), url)
        assertTrue(url.contains("client_id=the-client"), url)
        assertTrue(url.contains("code_challenge=the-challenge"), url)
        assertTrue(url.contains("code_challenge_method=S256"), url)
        assertTrue(url.contains("state=the-state"), url)
        assertTrue(url.contains("response_type=code"), url)
    }

    /**
     * Without both of these Google returns no refresh token, and the sign in works once and then
     * stops days later with no obvious cause.
     */
    @Test
    fun `the authorization url asks for a refresh token`() {
        val url = sut.authorizationUrl("https://e", "c", "r", "s", "ch", "st")

        assertTrue(url.contains("access_type=offline"), url)
        assertTrue(url.contains("prompt=consent"), url)
    }

    /** The redirect and the scope both carry characters that change meaning if they are not escaped. */
    @Test
    fun `the redirect uri and scope are escaped`() {
        val url = sut.authorizationUrl(
            "https://e", "c", "http://localhost:8080/oauth/callback", "https://www.googleapis.com/auth/drive.file", "ch", "st"
        )

        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Foauth%2Fcallback"), url)
        assertTrue(url.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file"), url)
    }

    /** Google reads a raw plus as a space, so a value carrying one must arrive escaped. */
    @Test
    fun `a plus is escaped rather than read as a space`() {
        assertEquals("a%2Bb", sut.formEncode("a+b"))
    }

    @Test
    fun `unreserved characters are left alone`() {
        assertEquals("Aa0-._~", sut.formEncode("Aa0-._~"))
    }

    /** A state or scope is not promised to be ASCII, and a mangled state fails the check it exists for. */
    @Test
    fun `non ascii is escaped as utf-8 bytes`() {
        assertEquals("%C5%A1", sut.formEncode("š"))
    }
}
