package app.aaps.plugins.eversense.protocol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * The Eversense server calls (sign-in, token refresh, uploads, the 365 certificate request) for each model, region
 * and answer, against digests of what the first driver produced (see [GoldenDigests]): the result, what is left in
 * the store, and the HTTP request itself (method, path, headers, body). The calls go to a local mock server; no
 * Eversense server is contacted.
 */
class DmsHttpGoldenTest {

    private val fixedTime = 1_735_689_610_000L
    private val tokenJson = """{"access_token":"fresh-token","expires_in":3600,"token_type":"Bearer","expires":"2099-01-01T00:00:00Z","lastLogin":"2025-01-01T00:00:00Z"}"""
    private val certificateJson = """{"Status":"Success","StatusCode":200,"Result":{"Certificate":"0102","IsKeyAvailable":true}}"""
    private val golden = GoldenDigests("dms-http")

    /** One model's server helper on a store of its own, with its server addresses pointed at the mock server */
    private class Gateway(generation: String, region: Boolean) {

        private val prefix = "app.aaps.plugins.eversense"
        val owner: Any = Class.forName("$prefix.util.EversenseHttp${generation}Util").getField("Companion").get(null)
        val values = mutableMapOf<String, Any?>(
            "eversense_state_secure" to """{"username":"test+ä@example.test","password":"test&=password","isEuropeanRegion":$region}""",
            "eversense_access_token" to "cached-token", "eversense_access_token_expiry" to Long.MAX_VALUE
        )
        val store: Any
        private val restore = mutableListOf<() -> Unit>()

        init {
            val editorType = Class.forName("$prefix.storage.EversenseStore\$Editor")
            val pending = mutableMapOf<String, Any?>()
            val editor = Mockito.mock(editorType) { call ->
                when {
                    call.method.name.startsWith("put") -> {
                        pending[call.getArgument(0)] = call.getArgument<Any?>(1); call.mock
                    }

                    call.method.name == "commit"        -> {
                        values.putAll(pending); true
                    }

                    call.method.name == "apply"         -> {
                        values.putAll(pending); null
                    }

                    else                                -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            }
            store = Mockito.mock(Class.forName("$prefix.storage.EversenseStore")) { call ->
                when (call.method.name) {
                    "getString", "getLong", "getInt", "getBoolean" -> values[call.getArgument<String>(0)] ?: call.getArgument<Any?>(1)
                    "contains"                                     -> values.containsKey(call.getArgument<String>(0))
                    "edit"                                         -> editor
                    else                                           -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            }
        }

        fun route(server: MockWebServer, generation: String) {
            val properties = if (generation == "E3") listOf("tokenBaseUrl", "careBaseUrl") else
                listOf("tokenBaseUrl", "uploadBaseUrl", "careBaseUrl", "vaultBaseUrl", "euTokenBaseUrl", "euUploadBaseUrl", "euCareBaseUrl", "euVaultBaseUrl")
            for (property in properties) {
                val suffix = property.replaceFirstChar(Char::uppercase)
                val getter = owner.javaClass.methods.single { it.name.startsWith("get$suffix") && it.parameterCount == 0 }
                val setter = owner.javaClass.methods.single { it.name.startsWith("set$suffix") && it.parameterCount == 1 }
                val previous = getter.invoke(owner)
                restore.add { setter.invoke(owner, previous) }
                val segment = if (generation == "E3") "ous" else if (property.startsWith("eu")) "eu" else "us"
                setter.invoke(owner, server.url("/$segment/").toString())
            }
        }

        fun close() = restore.asReversed().forEach { it() }

        private fun trend(): Any = Class.forName("$prefix.enums.EversenseTrendArrow").enumConstants!!.single { (it as Enum<*>).name == "FLAT" }
        private fun readings(): List<Any> = listOf(
            Class.forName("$prefix.models.EversenseCGMResult").constructors.single { !it.isSynthetic && it.parameterCount == 5 }
                .newInstance(123, 1_735_689_610_000L, trend(), "00112233445566778899", "01020304")
        )

        fun invoke(operation: String): Any? {
            val args: Array<Any> = when (operation) {
                "login", "getOrRefreshToken" -> arrayOf(store)
                "putCurrentValues"           -> arrayOf(store, 123, 1_735_689_610_000L, trend(), 55, 80)
                "putDeviceEvents"            -> arrayOf(store, readings(), "T123", emptyList<Any>(), emptyList<Any>())
                "uploadGlucoseReadings"      -> arrayOf(store, readings(), "T123", "1.2.3")
                "getFleetSecretV2"           -> arrayOf(store, "test-token", byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), true, ByteArray(91) { it.toByte() })
                else                         -> error(operation)
            }
            // The helpers take the AAPS logger first and are suspend functions, which take a continuation last
            val method = owner.javaClass.methods.single {
                it.name == operation && it.parameterCount == args.size + 2 && it.parameterTypes[0] == AAPSLogger::class.java && it.parameterTypes.last() == Continuation::class.java
            }
            val callArgs = arrayOf<Any>(AAPSLoggerTest(), *args)
            return runBlocking { suspendCoroutineUninterceptedOrReturn<Any?> { continuation -> method.invoke(owner, *callArgs, continuation) } }
        }
    }

    @Test fun requestsAndOutcomesMatchTheFirstDriver() {
        for (generation in listOf("E3", "365")) for (eu in listOf(false, true)) {
            val operations = listOf("login", "putCurrentValues", "putDeviceEvents") +
                if (generation == "365") listOf("uploadGlucoseReadings", "getFleetSecretV2") else emptyList()
            for (operation in operations) for (status in listOf(200, 401, 500)) record(generation, eu, operation, status)
            record(generation, eu, "login", 200, malformed = true)
            record(generation, eu, "getOrRefreshToken", 200, expired = true)
        }
        golden.check()
    }

    /** A token that is still valid, also the empty one of an old install, is used with no network call. */
    @Test fun validCachedTokensNeedNoNetwork() {
        MockWebServer().use { server ->
            server.start()
            for (generation in listOf("E3", "365")) for (eu in listOf(false, true)) {
                val gateway = Gateway(generation, eu)
                try {
                    gateway.route(server, generation)
                    repeat(2) { server.enqueue(MockResponse.Builder().code(503).build()) }
                    for (token in listOf("offline-token", "")) {
                        gateway.values["eversense_access_token"] = token
                        gateway.values["eversense_access_token_expiry"] = fixedTime + 300_001L
                        PacketTime.withClock(Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneOffset.UTC)) {
                            assertEquals(token, gateway.invoke("getOrRefreshToken"))
                        }
                        golden.add("cached $generation eu=$eu", gateway.values.toSortedMap())
                    }
                    assertEquals(0, server.requestCount, "Cached tokens must not access the network")
                } finally {
                    gateway.close()
                }
            }
        }
        golden.check()
    }

    private fun record(generation: String, eu: Boolean, operation: String, status: Int, malformed: Boolean = false, expired: Boolean = false) {
        val group = "$generation eu=$eu $operation"
        val server = MockWebServer()
        server.start()
        val gateway = Gateway(generation, eu)
        val oldZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            gateway.route(server, generation)
            if (expired) gateway.values.remove("eversense_access_token")
            val body = when {
                malformed                                         -> "not-json"
                status >= 400                                     -> "{\"error\":\"denied\"}"
                operation in listOf("login", "getOrRefreshToken") -> tokenJson
                operation == "getFleetSecretV2"                   -> certificateJson
                else                                              -> "{\"ok\":true}"
            }
            server.enqueue(MockResponse.Builder().code(status).body(body).addHeader("Content-Type", "application/json; charset=utf-8").build())
            PacketTime.withClock(Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneOffset.UTC)) {
                golden.add(group, "HTTP$status malformed=$malformed result=${gateway.invoke(operation)}")
                // The one change on purpose from the first driver: a login the server refuses (401) is recorded, for
                // the user's "sign in again"; the rest of the store is as the first driver left it
                val refusedLogin = status == 401 && (operation == "login" || (operation == "getOrRefreshToken" && expired))
                assertEquals(refusedLogin, gateway.values.remove(StorageKeys.LOGIN_REFUSED) == true, "login refusal recorded")
                golden.add(group, gateway.values.toSortedMap())
            }
            val request = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(request)
            // null for a request without a body (a GET), as mockwebserver3 reports it
            golden.add(group, listOf(request!!.method, request.target, request.headers["Content-Type"], request.headers["Authorization"], request.body?.utf8()))
        } finally {
            gateway.close()
            TimeZone.setDefault(oldZone)
            server.close()
        }
    }
}
