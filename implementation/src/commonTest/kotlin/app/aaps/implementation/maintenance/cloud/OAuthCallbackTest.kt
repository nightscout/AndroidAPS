package app.aaps.implementation.maintenance.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The one request line that finishes a sign in, read the same way on every platform.
 *
 * `IosLoopbackAuthServerTest` proves the socket half on a simulator. This is the half that decides
 * what the request *means*, and it runs everywhere - so Android and iOS cannot disagree about a
 * redirect that arrives slightly differently than expected.
 */
class OAuthCallbackTest {

    @Test
    fun `a plain redirect gives up its code and state`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?code=abc123&state=xyz HTTP/1.1")

        val code = assertIs<OAuthCallback.Result.Code>(result)
        assertEquals("abc123", code.code)
        assertEquals("xyz", code.state)
    }

    @Test
    fun `the order of the parameters does not matter`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?state=xyz&code=abc123 HTTP/1.1")

        assertEquals("abc123", assertIs<OAuthCallback.Result.Code>(result).code)
    }

    /** Google sends this when the user presses cancel. It is an answer, not a failure of ours. */
    @Test
    fun `a refusal is a refusal and not an error`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?error=access_denied HTTP/1.1")

        assertEquals("access_denied", assertIs<OAuthCallback.Result.Denied>(result).error)
    }

    /** A browser asks for this before anything else. Treating it as the answer would end the wait. */
    @Test
    fun `a favicon request is not the callback`() {
        assertIs<OAuthCallback.Result.NotTheCallback>(OAuthCallback.parseRequestLine("GET /favicon.ico HTTP/1.1"))
    }

    @Test
    fun `another path is not the callback either`() {
        assertIs<OAuthCallback.Result.NotTheCallback>(OAuthCallback.parseRequestLine("GET /something/else?code=abc HTTP/1.1"))
    }

    /**
     * An empty code would be sent to Google and fail there, with an error describing nothing. Better
     * to notice it here.
     */
    @Test
    fun `a callback carrying no code is malformed`() {
        assertIs<OAuthCallback.Result.Malformed>(OAuthCallback.parseRequestLine("GET /oauth/callback?code= HTTP/1.1"))
        assertIs<OAuthCallback.Result.Malformed>(OAuthCallback.parseRequestLine("GET /oauth/callback HTTP/1.1"))
        assertIs<OAuthCallback.Result.Malformed>(OAuthCallback.parseRequestLine("GET /oauth/callback? HTTP/1.1"))
    }

    @Test
    fun `nonsense is malformed rather than an exception`() {
        assertIs<OAuthCallback.Result.Malformed>(OAuthCallback.parseRequestLine(""))
        assertIs<OAuthCallback.Result.Malformed>(OAuthCallback.parseRequestLine("GET"))
    }

    /**
     * `state` is compared against what was sent, so a half-decoded one fails a check that exists to
     * catch a forged redirect.
     */
    @Test
    fun `percent encoding is decoded`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?code=a%2Fb&state=x%20y HTTP/1.1")

        val code = assertIs<OAuthCallback.Result.Code>(result)
        assertEquals("a/b", code.code)
        assertEquals("x y", code.state)
    }

    @Test
    fun `a plus is a space the way a form encodes one`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?code=abc&state=x+y HTTP/1.1")

        assertEquals("x y", assertIs<OAuthCallback.Result.Code>(result).state)
    }

    /** A stray percent must not throw; the sign in should fail cleanly instead. */
    @Test
    fun `a broken escape is left as it stands`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?code=a%zzb HTTP/1.1")

        assertEquals("a%zzb", assertIs<OAuthCallback.Result.Code>(result).code)
    }

    @Test
    fun `a redirect with no state still works`() {
        val result = OAuthCallback.parseRequestLine("GET /oauth/callback?code=abc HTTP/1.1")

        assertEquals(null, assertIs<OAuthCallback.Result.Code>(result).state)
    }

    @Test
    fun `the browser is told it worked only when it did`() {
        val ok = OAuthCallback.responseFor(OAuthCallback.Result.Code("c", null), "done", "failed")
        val bad = OAuthCallback.responseFor(OAuthCallback.Result.Denied("access_denied"), "done", "failed")

        assertTrue(ok.startsWith("HTTP/1.1 200 OK"))
        assertTrue(ok.contains("done"))
        assertTrue(bad.startsWith("HTTP/1.1 400"))
        assertTrue(bad.contains("failed"))
    }

    /** A browser needs the length to stop reading, or the page hangs looking finished. */
    @Test
    fun `the response declares the length of its body`() {
        val response = OAuthCallback.responseFor(OAuthCallback.Result.Code("c", null), "done", "failed")

        val body = response.substringAfter("\r\n\r\n")
        assertTrue(response.contains("Content-Length: ${body.encodeToByteArray().size}"), response.substringBefore("\r\n\r\n"))
    }
}
