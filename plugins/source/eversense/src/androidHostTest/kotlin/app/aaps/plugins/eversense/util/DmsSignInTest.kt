package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.MemoryStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The sign-in check of the pairing wizard, and the refusal every later login records. What matters is telling a
 * refused login (the user must sign in again) from anything else (no internet, a server fault), which the user cannot
 * fix by typing.
 */
class DmsSignInTest {

    private val server = MockWebServer()
    private val accepted = """{"access_token":"token","expires_in":3600,"token_type":"Bearer","expires":"","lastLogin":""}"""

    @BeforeEach fun startServer() = server.start()
    @AfterEach fun stopServer() = server.close()

    private fun base() = server.url("/").toString()
    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test fun `an accepted login is OK`(): Unit = runBlocking {
        answer(200, accepted)
        assertThat(DmsSignIn.check(base(), "user", "secret")).isEqualTo(DmsSignIn.Outcome.OK)
    }

    @Test fun `a wrong username or password is refused`(): Unit = runBlocking {
        answer(400, """{"error":"invalid_grant"}""")
        assertThat(DmsSignIn.check(base(), "user", "wrong")).isEqualTo(DmsSignIn.Outcome.REFUSED)
        answer(401)
        assertThat(DmsSignIn.check(base(), "user", "wrong")).isEqualTo(DmsSignIn.Outcome.REFUSED)
    }

    /** A rejected app (client) or a server fault is not the user's login: asking to sign in again would not help. */
    @Test fun `another error is not about the login`(): Unit = runBlocking {
        answer(400, """{"error":"invalid_client"}""")
        assertThat(DmsSignIn.check(base(), "user", "secret")).isEqualTo(DmsSignIn.Outcome.SERVER_ERROR)
        answer(503)
        assertThat(DmsSignIn.check(base(), "user", "secret")).isEqualTo(DmsSignIn.Outcome.SERVER_ERROR)
    }

    @Test fun `no answer at all is no connection`(): Unit = runBlocking {
        val url = base()
        server.close()
        assertThat(DmsSignIn.check(url, "user", "secret")).isEqualTo(DmsSignIn.Outcome.NO_CONNECTION)
    }

    /** The same password grant the driver's own logins send, to the server of the chosen model. */
    @Test fun `the check is a password login at the token endpoint`(): Unit = runBlocking {
        answer(200, accepted)
        DmsSignIn.check(base(), "user@example.com", "secret")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.target).isEqualTo("/connect/token")
        assertThat(request.headers["Content-Type"]).startsWith("application/x-www-form-urlencoded")
        assertThat(request.body!!.utf8()).contains("grant_type=password")
        assertThat(request.body!!.utf8()).contains("username=user%40example.com")
    }

    // ----- what a later login records -----

    /** A refusal is kept until a login is accepted, so the badge stays until the user signed in again. */
    @Test fun `a refusal is kept until a login is accepted`() {
        val store = MemoryStore()
        DmsSignIn.remember(store, DmsSignIn.Outcome.REFUSED)
        assertThat(store.getBoolean(StorageKeys.LOGIN_REFUSED, false)).isTrue()

        DmsSignIn.remember(store, DmsSignIn.Outcome.NO_CONNECTION)
        DmsSignIn.remember(store, DmsSignIn.Outcome.SERVER_ERROR)
        assertThat(store.getBoolean(StorageKeys.LOGIN_REFUSED, false)).isTrue()

        DmsSignIn.remember(store, DmsSignIn.Outcome.OK)
        assertThat(store.contains(StorageKeys.LOGIN_REFUSED)).isFalse()
    }
}
