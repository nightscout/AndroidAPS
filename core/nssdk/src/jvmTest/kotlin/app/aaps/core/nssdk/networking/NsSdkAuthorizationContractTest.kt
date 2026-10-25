package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.exceptions.NotAllowedNightscoutException
import app.aaps.core.nssdk.exceptions.UnsuccessfulNightscoutException
import app.aaps.core.nssdk.localmodel.authorization.NSAuthRole
import app.aaps.core.nssdk.localmodel.authorization.NSAuthSubject
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the token and role calls (`/api/v2/authorization`) the master uses to make a Nightscout token for each client.
 *
 * The shapes are the ones of the Nightscout server code (`lib/authorization/endpoints.js` and `storage.js`): plain JSON
 * without the API v3 wrapper, a create that answers the stored document without the token, and 401 for a token that
 * may not do this.
 */
class NsSdkAuthorizationContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NSAndroidClientImpl

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NSAndroidClientImpl(
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "REFRESH_TOKEN",
            logging = false,
            logger = { },
            dispatcher = Dispatchers.Unconfined
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
    private fun status(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    @Test
    fun `roles are read from the v2 path, built-in ones without an id`() = runTest {
        server.enqueue(ok("""[{"_id":"r1","name":"aaps-client","permissions":["*:*:read"],"created_at":"2026-10-21"},{"name":"admin","permissions":["*"]}]"""))

        val roles = client.getAuthRoles()

        val sent = server.takeRequest()
        assertThat(sent.method).isEqualTo("GET")
        assertThat(sent.target).isEqualTo("/api/v2/authorization/roles")
        assertThat(roles).containsExactly(
            NSAuthRole(id = "r1", name = "aaps-client", permissions = listOf("*:*:read")),
            NSAuthRole(name = "admin", permissions = listOf("*"))
        ).inOrder()
    }

    /** A new role is sent without `_id`: Mongo would otherwise store a null id, and the next create would fail. */
    @Test
    fun `a new role is posted without an id and comes back with one`() = runTest {
        server.enqueue(ok("""{"_id":"r2","name":"aaps-client","permissions":["*:*:read"],"notes":"n","created_at":"2026-10-21"}"""))

        val stored = client.createAuthRole(NSAuthRole(name = "aaps-client", permissions = listOf("*:*:read"), notes = "n"))

        val sent = server.takeRequest()
        assertThat(sent.method).isEqualTo("POST")
        assertThat(sent.target).isEqualTo("/api/v2/authorization/roles")
        val body = Json.parseToJsonElement(sent.body!!.utf8()).jsonObject
        assertThat(body.keys).doesNotContain("_id")
        assertThat(body["name"]!!.jsonPrimitive.content).isEqualTo("aaps-client")
        assertThat(body["permissions"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("*:*:read")
        assertThat(stored.id).isEqualTo("r2")
    }

    @Test
    fun `a changed role is put back as a whole, with its id`() = runTest {
        server.enqueue(ok("""{"_id":"r1","name":"aaps-client","permissions":["*:*:read"]}"""))

        client.updateAuthRole(NSAuthRole(id = "r1", name = "aaps-client", permissions = listOf("*:*:read")))

        val sent = server.takeRequest()
        assertThat(sent.method).isEqualTo("PUT")
        assertThat(sent.target).isEqualTo("/api/v2/authorization/roles")
        assertThat(Json.parseToJsonElement(sent.body!!.utf8()).jsonObject["_id"]!!.jsonPrimitive.content).isEqualTo("r1")
    }

    @Test
    fun `subjects are listed with their tokens`() = runTest {
        server.enqueue(ok("""[{"_id":"s1","name":"aaps-Tablet","accessToken":"aapstablet-0123456789abcdef","roles":["aaps-client"]}]"""))

        val subjects = client.getAuthSubjects()

        assertThat(server.takeRequest().target).isEqualTo("/api/v2/authorization/subjects")
        assertThat(subjects.single().accessToken).isEqualTo("aapstablet-0123456789abcdef")
        assertThat(subjects.single().roles).containsExactly("aaps-client")
    }

    @Test
    fun `a new subject is posted without id and token`() = runTest {
        server.enqueue(ok("""{"_id":"s2","name":"aaps-Tablet","roles":["aaps-client"],"created_at":"2026-10-21"}"""))

        val created = client.createAuthSubject(NSAuthSubject(name = "aaps-Tablet", roles = listOf("aaps-client")))

        val sent = server.takeRequest()
        assertThat(sent.method).isEqualTo("POST")
        assertThat(sent.target).isEqualTo("/api/v2/authorization/subjects")
        val body = Json.parseToJsonElement(sent.body!!.utf8()).jsonObject
        assertThat(body.keys).containsNoneOf("_id", "accessToken")
        assertThat(created.id).isEqualTo("s2")
        assertThat(created.accessToken).isNull()
    }

    @Test
    fun `a subject is deleted by its id in the path`() = runTest {
        server.enqueue(ok("{}"))

        client.deleteAuthSubject("s1")

        val sent = server.takeRequest()
        assertThat(sent.method).isEqualTo("DELETE")
        assertThat(sent.target).isEqualTo("/api/v2/authorization/subjects/s1")
    }

    /**
     * A token that is not an admin token: Nightscout answers 401 on API v2 also after a good login. That is not an
     * invalid token, and it is not tried again: the answer would be the same.
     */
    @Test
    fun `no admin rights is NotAllowed and is not repeated`() = runTest {
        val dataCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.target.contains("/authorization/request/")) ok("""{"token":"JWT","iat":1,"exp":2}""")
                else {
                    dataCalls.incrementAndGet()
                    status(401, """{"status":401,"message":"Unauthorized","description":"Invalid/Missing"}""")
                }
        }

        assertThrows<NotAllowedNightscoutException> { client.getAuthSubjects() }
        assertThat(dataCalls.get()).isEqualTo(2) // the call and its one replay after the login, no retries
    }

    /** A create is sent once: repeating a create whose answer was lost would make a second working token. */
    @Test
    fun `a failed create is not repeated`() = runTest {
        val calls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls.incrementAndGet()
                return status(500, "Mongo Error")
            }
        }

        assertThrows<UnsuccessfulNightscoutException> { client.createAuthSubject(NSAuthSubject(name = "aaps-Tablet", roles = listOf("aaps-client"))) }
        assertThat(calls.get()).isEqualTo(1)
    }

    private fun statusBody(rights: String) =
        """{"status":200,"result":{"version":"15.0.3","apiVersion":"3.0.4","srvDate":1,"storage":{"storage":"mongodb","version":"7"},""" +
            """"apiPermissions":{"devicestatus":"$rights","entries":"$rights","food":"$rights","profile":"$rights","settings":"$rights","treatments":"$rights"}}}"""

    /**
     * What a live Nightscout answers for the client role: `v3/status` says read-only everywhere, settings too, because
     * it only sees wildcard rights. The settings rights are then asked one by one, and the client token has them.
     */
    @Test
    fun `the settings rights of a client token are asked, not taken from v3 status`() = runTest {
        val checked = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.target.contains("/authorization/request/") -> ok("""{"token":"JWT","iat":1,"exp":2}""")
                request.target.contains("/debug/check/")           -> {
                    checked += request.target.substringAfter("/debug/check/")
                    ok("""{"check":true}""")
                }

                else                                                -> ok(statusBody("r"))
            }
        }

        val permissions = client.getStatus().apiPermissions

        assertThat(checked).containsExactly("api:settings:create", "api:settings:update")
        assertThat(permissions.settings.create).isTrue()
        assertThat(permissions.settings.update).isTrue()
        assertThat(permissions.isClient()).isTrue()
    }

    /** The built-in `readable` role: Nightscout answers 401 to the check, so it stays read-only. */
    @Test
    fun `a read-only token stays read-only`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.target.contains("/authorization/request/") -> ok("""{"token":"JWT","iat":1,"exp":2}""")
                request.target.contains("/debug/check/")           -> status(401, """{"status":401,"message":"Unauthorized"}""")
                else                                                -> ok(statusBody("r"))
            }
        }

        assertThat(client.getStatus().apiPermissions.isClient()).isFalse()
    }

    /** An admin token shows full rights in `v3/status`, so nothing more is asked. */
    @Test
    fun `full rights are not asked again`() = runTest {
        val checks = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.target.contains("/debug/check/")) checks.incrementAndGet()
                return ok(statusBody("crud"))
            }
        }

        assertThat(client.getStatus().apiPermissions.isFull()).isTrue()
        assertThat(checks.get()).isEqualTo(0)
    }

    @Test
    fun `a subject printed in a log hides its token`() {
        val subject = NSAuthSubject(id = "s1", name = "aaps-Tablet", accessToken = "aapstablet-0123456789abcdef")

        assertThat(subject.toString()).doesNotContain("0123456789abcdef")
    }
}
