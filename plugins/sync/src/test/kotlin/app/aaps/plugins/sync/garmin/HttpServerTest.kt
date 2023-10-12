package app.aaps.plugins.sync.garmin

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketAddress
import java.net.URI
import java.nio.charset.Charset
import java.time.Duration

internal class HttpServerTest: TestBase() {

    private fun toInputStream(s: String): InputStream {
        return ByteArrayInputStream(s.toByteArray(Charset.forName("ASCII")))
    }

    @Test fun testReadBody() {
        val input = toInputStream("Test")
        assertEquals("Test", HttpServer.readBody(input, 100))
    }

    @Test fun testReadBody_MoreContentThanLength() {
        val input = toInputStream("Test")
        assertEquals("Tes", HttpServer.readBody(input, 3))
    }

    @Test fun testParseRequest_Get() {
        val req = """ 
            GET http://foo HTTP/1.1
            """.trimIndent()
        assertEquals(
            URI("http://foo") to null,
            HttpServer.parseRequest(toInputStream(req)))
    }

    @Test fun testParseRequest_PostEmptyBody() {
        val req = """ 
            POST http://foo HTTP/1.1
            """.trimIndent()
        assertEquals(
            URI("http://foo") to null,
            HttpServer.parseRequest(toInputStream(req)))
    }

    @Test fun testParseRequest_PostBody() {
        val req = """ 
            POST http://foo HTTP/1.1
            Content-Type: application/x-www-form-urlencoded
            
            a=1&b=2
            """.trimIndent()
        assertEquals(
            URI("http://foo?a=1&b=2") to null,
            HttpServer.parseRequest(toInputStream(req)))
    }

    @Test fun testParseRequest_PostBodyContentLength() {
        val req = """ 
            POST http://foo HTTP/1.1
            Content-Type: application/x-www-form-urlencoded
            Content-Length: 3
            
            a=1&b=2
            """.trimIndent()
        assertEquals(
            URI("http://foo?a=1") to null,
            HttpServer.parseRequest(toInputStream(req)))
    }

    @Test fun testRequest() {
        val port = 28895
        val reqUri = URI("http://127.0.0.1:$port/foo")
        HttpServer(aapsLogger, port).use { server ->
            server.registerEndpoint("/foo")  { _: SocketAddress, uri: URI, _: String? ->
                    assertEquals(URI("/foo"), uri)
                    "test"
                }
            assertTrue(server.awaitReady(Duration.ofSeconds(10)))
            val resp = reqUri.toURL().openConnection() as HttpURLConnection
            assertEquals(200, resp.responseCode)
            val content = (resp.content as InputStream).reader().use { r -> r.readText() }
            assertEquals("test", content)
        }
    }

    @Test fun testRequest_NotFound() {
        val port = 28895
        val reqUri = URI("http://127.0.0.1:$port/foo")
        HttpServer(aapsLogger, port).use { server ->
            assertTrue(server.awaitReady(Duration.ofSeconds(10)))
            val resp = reqUri.toURL().openConnection() as HttpURLConnection
            assertEquals(404, resp.responseCode)
        }
    }
}
