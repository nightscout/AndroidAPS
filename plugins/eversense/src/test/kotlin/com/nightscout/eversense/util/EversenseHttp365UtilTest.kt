package com.nightscout.eversense.util

import android.content.SharedPreferences
import com.nightscout.eversense.enums.EversenseTrendArrow
import com.nightscout.eversense.models.EversenseCGMResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EversenseHttp365UtilTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val validTokenJson = """
        {
            "access_token": "test_access_token_abc123",
            "expires_in": 3600,
            "token_type": "Bearer",
            "expires": "2099-01-01T00:00:00Z",
            "lastLogin": "2026-04-10T00:00:00Z"
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        EversenseHttp365Util.tokenBaseUrl = baseUrl
        EversenseHttp365Util.uploadBaseUrl = baseUrl

        prefs = mock()
        editor = mock()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.commit()).thenReturn(true)
        whenever(editor.apply()).then { }

        // Default: no stored state (empty secure state)
        whenever(prefs.getString(StorageKeys.SECURE_STATE, null)).thenReturn(null)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        // Restore production URLs
        EversenseHttp365Util.tokenBaseUrl = "https://usiamapi.eversensedms.com/"
        EversenseHttp365Util.uploadBaseUrl = "https://usmobileappmsprod.eversensedms.com/"
    }

    // ─── getOrRefreshToken ────────────────────────────────────────────────────

    @Test
    fun `getOrRefreshToken returns cached token when not expired`() {
        val futureExpiry = System.currentTimeMillis() + 600_000L // 10 minutes from now
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(futureExpiry)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("cached_token_xyz")

        val token = EversenseHttp365Util.getOrRefreshToken(prefs)

        assertEquals("cached_token_xyz", token)
        // No requests should have been made to the server
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `getOrRefreshToken fetches new token when cache is expired`() {
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(0L)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn(null)
        whenever(prefs.getString(StorageKeys.SECURE_STATE, null)).thenReturn(
            """{"username":"user@example.com","password":"testpass"}"""
        )

        mockWebServer.enqueue(MockResponse().setBody(validTokenJson).setResponseCode(200))

        val token = EversenseHttp365Util.getOrRefreshToken(prefs)

        assertEquals("test_access_token_abc123", token)
        assertEquals(1, mockWebServer.requestCount)
        val request = mockWebServer.takeRequest()
        assertEquals("/connect/token", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("grant_type=password"))
    }

    @Test
    fun `getOrRefreshToken returns null when login fails`() {
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(0L)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn(null)

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_client"}"""))

        val token = EversenseHttp365Util.getOrRefreshToken(prefs)

        assertNull(token)
    }

    @Test
    fun `getOrRefreshToken refreshes token within 5 minutes of expiry`() {
        // Token expires in 4 minutes — within the 5-minute refresh window
        val nearExpiryMs = System.currentTimeMillis() + 240_000L
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(nearExpiryMs)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("old_token")
        whenever(prefs.getString(StorageKeys.SECURE_STATE, null)).thenReturn(
            """{"username":"user@example.com","password":"testpass"}"""
        )

        mockWebServer.enqueue(MockResponse().setBody(validTokenJson).setResponseCode(200))

        val token = EversenseHttp365Util.getOrRefreshToken(prefs)

        assertEquals("test_access_token_abc123", token)
        assertEquals(1, mockWebServer.requestCount)
    }

    // ─── uploadGlucoseReadings ────────────────────────────────────────────────

    @Test
    fun `uploadGlucoseReadings posts to correct endpoint with bearer token`() {
        val futureExpiry = System.currentTimeMillis() + 600_000L
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(futureExpiry)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("my_bearer_token")

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val readings = listOf(
            EversenseCGMResult(
                glucoseInMgDl = 120,
                datetime = 1700000000000L,
                trend = EversenseTrendArrow.FLAT,
                sensorId = "sensor_001",
                rawResponseHex = "deadbeef"
            )
        )

        val result = EversenseHttp365Util.uploadGlucoseReadings(prefs, readings, "TX-12345", "1.2.3")

        assertTrue(result, "Expected upload to return true on HTTP 200")
        assertEquals(1, mockWebServer.requestCount)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/v1.0/DiagnosticLog/PostEssentialLogs", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer my_bearer_token", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))
    }

    @Test
    fun `uploadGlucoseReadings sends correct JSON body fields`() {
        val futureExpiry = System.currentTimeMillis() + 600_000L
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(futureExpiry)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("my_bearer_token")

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val readings = listOf(
            EversenseCGMResult(
                glucoseInMgDl = 95,
                datetime = 1700000000000L,
                trend = EversenseTrendArrow.FLAT,
                sensorId = "abc123",
                rawResponseHex = "cafebabe"
            )
        )

        EversenseHttp365Util.uploadGlucoseReadings(prefs, readings, "TXSERIAL", "2.0.1")

        val body = mockWebServer.takeRequest().body.readUtf8()

        assertTrue(body.startsWith("[") && body.endsWith("]"), "Body must be a bare JSON array")
        assertTrue(body.contains("\"SensorId\":\"abc123\""), "Missing SensorId")
        assertTrue(body.contains("\"TransmitterId\":\"TXSERIAL\""), "Missing TransmitterId")
        assertTrue(body.contains("\"CurrentGlucoseValue\":95"), "Missing CurrentGlucoseValue")
        assertTrue(body.contains("\"FWVersion\":\"2.0.1\""), "Missing FWVersion")
        // EssentialLog must be base64-encoded bytes, not a hex string
        val expectedBase64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte()))
        assertTrue(body.contains("\"EssentialLog\":\"$expectedBase64\""), "EssentialLog must be base64 bytes, got: $body")
    }

    @Test
    fun `uploadGlucoseReadings sends multiple readings in one request`() {
        val futureExpiry = System.currentTimeMillis() + 600_000L
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(futureExpiry)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("token")

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val readings = listOf(
            EversenseCGMResult(100, 1700000000000L, EversenseTrendArrow.FLAT, "s1", "aa"),
            EversenseCGMResult(110, 1700000300000L, EversenseTrendArrow.SINGLE_UP, "s1", "bb"),
            EversenseCGMResult(105, 1700000600000L, EversenseTrendArrow.SINGLE_DOWN, "s1", "cc")
        )

        EversenseHttp365Util.uploadGlucoseReadings(prefs, readings, "TX99", "3.0")

        assertEquals(1, mockWebServer.requestCount)
        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"CurrentGlucoseValue\":100"))
        assertTrue(body.contains("\"CurrentGlucoseValue\":110"))
        assertTrue(body.contains("\"CurrentGlucoseValue\":105"))
    }

    @Test
    fun `uploadGlucoseReadings does nothing when readings list is empty`() {
        EversenseHttp365Util.uploadGlucoseReadings(prefs, emptyList(), "TX99", "1.0")

        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `uploadGlucoseReadings does not throw on 4xx server error`() {
        val futureExpiry = System.currentTimeMillis() + 600_000L
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(futureExpiry)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("token")

        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))

        val readings = listOf(
            EversenseCGMResult(120, System.currentTimeMillis(), EversenseTrendArrow.FLAT, "s1", "ff")
        )

        // Should not throw — errors are logged internally, returns false
        val result = EversenseHttp365Util.uploadGlucoseReadings(prefs, readings, "TX1", "1.0")

        assertFalse(result, "Expected upload to return false on HTTP 400")
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `uploadGlucoseReadings does not throw on 5xx server error`() {
        val futureExpiry = System.currentTimeMillis() + 600_000L
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(futureExpiry)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn("token")

        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val readings = listOf(
            EversenseCGMResult(80, System.currentTimeMillis(), EversenseTrendArrow.FLAT, "s1", "01")
        )

        val result = EversenseHttp365Util.uploadGlucoseReadings(prefs, readings, "TX1", "1.0")

        assertFalse(result, "Expected upload to return false on HTTP 500")
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `uploadGlucoseReadings skips upload when no valid token available`() {
        // Token expired and login fails
        whenever(prefs.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)).thenReturn(0L)
        whenever(prefs.getString(StorageKeys.ACCESS_TOKEN, null)).thenReturn(null)

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        val readings = listOf(
            EversenseCGMResult(100, System.currentTimeMillis(), EversenseTrendArrow.FLAT, "s1", "ab")
        )

        EversenseHttp365Util.uploadGlucoseReadings(prefs, readings, "TX1", "1.0")

        // Only the login attempt should have been made, not the upload
        assertEquals(1, mockWebServer.requestCount)
        assertEquals("/connect/token", mockWebServer.takeRequest().path)
    }

    // ─── login ───────────────────────────────────────────────────────────────

    @Test
    fun `login sends correct form-encoded body`() {
        whenever(prefs.getString(StorageKeys.SECURE_STATE, null)).thenReturn(
            """{"username":"testuser@test.com","password":"secret123"}"""
        )

        mockWebServer.enqueue(MockResponse().setBody(validTokenJson).setResponseCode(200))

        val result = EversenseHttp365Util.login(prefs)

        assertNotNull(result)
        assertEquals("test_access_token_abc123", result!!.access_token)
        assertEquals(3600, result.expires_in)

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("grant_type=password"))
        assertTrue(body.contains("client_id=eversenseMMAAndroid"))
        assertTrue(body.contains("username=testuser%40test.com") || body.contains("username=testuser@test.com"))
    }

    @Test
    fun `login returns null on 401 response`() {
        whenever(prefs.getString(StorageKeys.SECURE_STATE, null)).thenReturn(
            """{"username":"bad@user.com","password":"wrong"}"""
        )

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_grant"}"""))

        val result = EversenseHttp365Util.login(prefs)

        assertNull(result)
    }
}
