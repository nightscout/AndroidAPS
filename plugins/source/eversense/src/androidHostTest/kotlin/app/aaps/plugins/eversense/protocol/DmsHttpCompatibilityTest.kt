package app.aaps.plugins.eversense.protocol

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito

/** Observe actual HTTP paths/headers/bodies, not just model-level mocks. No manufacturer endpoint is contacted. */
class DmsHttpCompatibilityTest {
    private val fixedTime = 1_735_689_610_000L
    private val tokenJson = """{"access_token":"fresh-token","expires_in":3600,"token_type":"Bearer","expires":"2099-01-01T00:00:00Z","lastLogin":"2025-01-01T00:00:00Z"}"""
    private val certificateJson = """{"Status":"Success","StatusCode":200,"Result":{"Certificate":"0102","IsKeyAvailable":true}}"""

    private class Gateway(val prefix: String, generation: String, val region: Boolean) {
        val owner: Any = Class.forName("$prefix.util.EversenseHttp${generation}Util").getField("Companion").get(null)
        val values = mutableMapOf<String, Any?>(
            "eversense_state_secure" to """{"username":"test+ä@example.test","password":"test&=password","isEuropeanRegion":$region}""",
            "eversense_access_token" to "cached-token", "eversense_access_token_expiry" to Long.MAX_VALUE
        )
        val store: Any
        private val restore = mutableListOf<() -> Unit>()
        init {
            val editorType = Class.forName(prefix + ".storage.EversenseStore\$Editor")
            val pending = mutableMapOf<String, Any?>()
            val editor = Mockito.mock(editorType) { call ->
                when {
                    call.method.name.startsWith("put") -> { pending[call.getArgument(0)] = call.getArgument<Any?>(1); call.mock }
                    call.method.name == "commit" -> { values.putAll(pending); true }
                    call.method.name == "apply" -> { values.putAll(pending); null }
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            }
            store = Mockito.mock(Class.forName("$prefix.storage.EversenseStore")) { call ->
                when (call.method.name) {
                    "getString", "getLong", "getInt", "getBoolean" -> values[call.getArgument<String>(0)] ?: call.getArgument<Any?>(1)
                    "contains" -> values.containsKey(call.getArgument<String>(0))
                    "edit" -> editor
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
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
        fun close() { restore.asReversed().forEach { it() } }
        private fun trend(): Any = Class.forName("$prefix.enums.EversenseTrendArrow").enumConstants!!.single { (it as Enum<*>).name == "FLAT" }
        private fun readings(): List<Any> = listOf(Class.forName("$prefix.models.EversenseCGMResult").constructors.single { !it.isSynthetic && it.parameterCount == 5 }
            .newInstance(123, 1_735_689_610_000L, trend(), "00112233445566778899", "01020304"))
        fun invoke(operation: String): Any? {
            val args: Array<Any> = when (operation) {
                "login", "getOrRefreshToken" -> arrayOf(store)
                "putCurrentValues" -> arrayOf(store, 123, 1_735_689_610_000L, trend(), 55, 80)
                "putDeviceEvents" -> arrayOf(store, readings(), "T123", emptyList<Any>(), emptyList<Any>())
                "uploadGlucoseReadings" -> arrayOf(store, readings(), "T123", "1.2.3")
                "getFleetSecretV2" -> arrayOf(store, "test-token", byteArrayOf(1,2,3), byteArrayOf(4,5,6), true, ByteArray(91) { it.toByte() })
                else -> error(operation)
            }
            return owner.javaClass.methods.single { it.name == operation && it.parameterCount == args.size }.invoke(owner, *args)
        }
    }

    @TestFactory fun modelSpecificRequestsAndOutcomes(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (generation in listOf("E3", "365")) for (eu in listOf(false, true)) {
            val operations = listOf("login", "putCurrentValues", "putDeviceEvents") +
                if (generation == "365") listOf("uploadGlucoseReadings", "getFleetSecretV2") else emptyList()
            for (operation in operations) for (status in listOf(200, 401, 500)) {
                tests.add(DynamicTest.dynamicTest("$generation region=$eu $operation HTTP$status") { compare(generation, eu, operation, status) })
            }
            tests.add(DynamicTest.dynamicTest("$generation region=$eu malformed login") { compare(generation, eu, "login", 200, malformed = true) })
            tests.add(DynamicTest.dynamicTest("$generation region=$eu token refresh") { compare(generation, eu, "getOrRefreshToken", 200, expired = true) })
        }
        return tests
    }

    @Test fun validCachedTokensNeedNoNetworkIncludingEmptyLegacyToken() {
        MockWebServer().use { server ->
            server.start()
            for (generation in listOf("E3", "365")) for (eu in listOf(false, true)) {
                val original = Gateway("app.aaps.plugins.eversense.reference", generation, eu)
                val rewritten = Gateway("app.aaps.plugins.eversense", generation, eu)
                try {
                    original.route(server, generation)
                    rewritten.route(server, generation)
                    repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }
                    for (token in listOf("offline-token", "")) {
                        for (gateway in listOf(original, rewritten)) {
                            gateway.values["eversense_access_token"] = token
                            gateway.values["eversense_access_token_expiry"] = fixedTime + 300_001L
                        }
                        PacketTime.withClock(Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneOffset.UTC)) {
                            assertEquals(token, original.invoke("getOrRefreshToken"))
                            assertEquals(token, rewritten.invoke("getOrRefreshToken"))
                            assertEquals(original.values, rewritten.values)
                        }
                    }
                    assertEquals(0, server.requestCount, "Cached tokens must not access the network")
                } finally {
                    original.close()
                    rewritten.close()
                }
            }
        }
    }

    private fun compare(generation: String, eu: Boolean, operation: String, status: Int, malformed: Boolean = false, expired: Boolean = false) {
        val server = MockWebServer()
        server.start()
        val original = Gateway("app.aaps.plugins.eversense.reference", generation, eu)
        val rewritten = Gateway("app.aaps.plugins.eversense", generation, eu)
        val oldZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            original.route(server, generation); rewritten.route(server, generation)
            if (expired) { original.values.remove("eversense_access_token"); rewritten.values.remove("eversense_access_token") }
            val body = when {
                malformed -> "not-json"
                status >= 400 -> "{\"error\":\"denied\"}"
                operation in listOf("login", "getOrRefreshToken") -> tokenJson
                operation == "getFleetSecretV2" -> certificateJson
                else -> "{\"ok\":true}"
            }
            repeat(2) { server.enqueue(MockResponse().setResponseCode(status).setBody(body).addHeader("Content-Type", "application/json; charset=utf-8")) }
            PacketTime.withClock(Clock.fixed(Instant.ofEpochMilli(fixedTime), ZoneOffset.UTC)) {
                val expected = original.invoke(operation)
                val actual = rewritten.invoke(operation)
                assertEquals(expected?.toString(), actual?.toString(), "result")
                assertEquals(original.values, rewritten.values, "credential/token store")
            }
            val expectedRequest = server.takeRequest(5, TimeUnit.SECONDS)
            val actualRequest = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(expectedRequest); assertNotNull(actualRequest)
            assertEquals(expectedRequest!!.method, actualRequest!!.method)
            assertEquals(expectedRequest.path, actualRequest.path)
            assertEquals(expectedRequest.getHeader("Content-Type"), actualRequest.getHeader("Content-Type"))
            assertEquals(expectedRequest.getHeader("Authorization"), actualRequest.getHeader("Authorization"))
            assertEquals(expectedRequest.body.readUtf8(), actualRequest.body.readUtf8(), "HTTP payload")
        } finally {
            original.close(); rewritten.close()
            TimeZone.setDefault(oldZone)
            server.shutdown()
        }
    }
}
