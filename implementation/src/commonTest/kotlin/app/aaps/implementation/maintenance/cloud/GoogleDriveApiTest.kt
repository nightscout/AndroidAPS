package app.aaps.implementation.maintenance.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Google Drive calls, against scripted answers.
 *
 * Drive cannot be reached from a test, and should not be: a suite that needed a Google account would
 * run for nobody. Ktor's mock engine answers instead, which lets the parts that are actually easy to
 * get wrong be checked exactly - the query a listing sends, the shape of an upload body, and which
 * failures mean the user has to sign in again.
 *
 * These run on every platform, because the same client serves all of them.
 */
class GoogleDriveApiTest {

    private val requests = mutableListOf<Pair<String, String>>()

    private fun apiReplying(
        vararg replies: Pair<HttpStatusCode, String>,
        token: Result<String> = Result.success("access-1")
    ): GoogleDriveApi {
        var call = 0
        val engine = MockEngine { request ->
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            requests += request.url.toString() to body
            val (status, reply) = replies[minOf(call++, replies.size - 1)]
            respond(ByteReadChannel(reply), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return GoogleDriveApi(HttpClient(engine), accessToken = { _ -> token })
    }

    // ----- Listing -----

    @Test
    fun `a listing returns the files and the token for the next page`() = runTest {
        val api = apiReplying(
            HttpStatusCode.OK to """
                {"files":[{"id":"1","name":"a.json","modifiedTime":"2026-09-05T01:00:00Z"},
                          {"id":"2","name":"b.json","modifiedTime":"2026-09-04T01:00:00Z"}],
                 "nextPageToken":"more"}
            """.trimIndent()
        )

        val page = api.listSettingsFiles("folder-1", pageToken = null).getOrThrow()

        assertEquals(listOf("a.json", "b.json"), page.files.map { it.name })
        assertEquals("1", page.files.first().id)
        assertEquals("more", page.nextPageToken)
    }

    @Test
    fun `the last page has no next token`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to """{"files":[]}""")

        assertEquals(null, api.listSettingsFiles("folder-1", null).getOrThrow().nextPageToken)
    }

    /** Folders are not backups. A listing that showed them would offer the user a folder to import. */
    @Test
    fun `a listing asks only for files in the folder that are not folders`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to """{"files":[]}""")

        api.listSettingsFiles("folder-1", null)

        val url = requests.single().first
        assertTrue(url.contains("folder-1"), url)
        assertTrue(url.contains("trashed"), url)
        assertTrue(url.contains("mimeType"), url)
    }

    /** Newest first, because a user restoring a backup almost always wants the last one. */
    @Test
    fun `a listing asks for the newest first`() = runTest {
        apiReplying(HttpStatusCode.OK to """{"files":[]}""").listSettingsFiles("f", null)

        assertTrue(requests.single().first.contains("orderBy=modifiedTime"), requests.single().first)
    }

    /** Drive returns everything it knows unless told otherwise, and a year of backups is a lot of it. */
    @Test
    fun `a listing asks only for the fields the screen shows`() = runTest {
        apiReplying(HttpStatusCode.OK to """{"files":[]}""").listSettingsFiles("f", null)

        assertTrue(requests.single().first.contains("fields="), requests.single().first)
    }

    @Test
    fun `a page token is passed on`() = runTest {
        apiReplying(HttpStatusCode.OK to """{"files":[]}""").listSettingsFiles("f", "the-token")

        assertTrue(requests.single().first.contains("pageToken=the-token"), requests.single().first)
    }

    @Test
    fun `a file with no id is skipped rather than crashing the list`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to """{"files":[{"name":"no-id.json"},{"id":"2","name":"fine.json"}]}""")

        assertEquals(listOf("fine.json"), api.listSettingsFiles("f", null).getOrThrow().files.map { it.name })
    }

    // ----- Uploading -----

    @Test
    fun `an upload returns the new file id`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to """{"id":"new-file"}""")

        assertEquals("new-file", api.uploadFile("export.json", "{}".encodeToByteArray(), "folder-1").getOrThrow())
    }

    /**
     * `multipart/related`, Drive's own upload shape - not the `multipart/form-data` a client library
     * builds. The two look alike and Drive ignores the second.
     */
    @Test
    fun `an upload sends metadata and content as one related body`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to """{"id":"new-file"}""")

        api.uploadFile("export.json", """{"format":"aaps_encrypted"}""".encodeToByteArray(), "folder-1")

        val (url, body) = requests.single()
        assertTrue(url.contains("uploadType=multipart"), url)
        assertTrue(body.contains("\"name\":\"export.json\""), body)
        assertTrue(body.contains("\"parents\":[\"folder-1\"]"), body)
        assertTrue(body.contains("""{"format":"aaps_encrypted"}"""), body)
        assertTrue(body.contains("Content-Type: application/json"), body)
    }

    /** A body that does not close its boundary is a body Drive rejects. */
    @Test
    fun `the upload body opens and closes its boundary`() = runTest {
        apiReplying(HttpStatusCode.OK to """{"id":"x"}""").uploadFile("a.json", "x".encodeToByteArray(), "f")

        val body = requests.single().second
        val boundary = body.substringBefore("\r\n").removePrefix("--")
        assertTrue(body.startsWith("--$boundary"), body.take(80))
        assertTrue(body.endsWith("--$boundary--"), body.takeLast(80))
    }

    // ----- Downloading -----

    @Test
    fun `a download asks for the content and not the description`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to """{"format":"aaps_encrypted"}""")

        val bytes = api.downloadFile("file-1").getOrThrow()

        assertTrue(requests.single().first.contains("alt=media"), requests.single().first)
        assertEquals("""{"format":"aaps_encrypted"}""", bytes.decodeToString())
    }

    // ----- Folders -----

    @Test
    fun `an existing folder path is found rather than made again`() = runTest {
        val api = apiReplying(
            HttpStatusCode.OK to """{"files":[{"id":"aaps-id","name":"AAPS"}]}""",
            HttpStatusCode.OK to """{"files":[{"id":"exports-id","name":"Exports"}]}"""
        )

        assertEquals("exports-id", api.folderIdForPath("AAPS/Exports").getOrThrow())
        assertEquals(2, requests.size, "nothing should have been created")
    }

    @Test
    fun `a folder that is missing is created`() = runTest {
        val api = apiReplying(
            HttpStatusCode.OK to """{"files":[]}""",
            HttpStatusCode.OK to """{"id":"made-id"}"""
        )

        assertEquals("made-id", api.folderIdForPath("AAPS").getOrThrow())
        assertTrue(requests.last().second.contains("application/vnd.google-apps.folder"), requests.last().second)
    }

    // ----- Failures -----

    /** A 401 is the sign in being finished, and the user has to do something about it. */
    @Test
    fun `an unauthorized answer says the sign in has expired`() = runTest {
        val api = apiReplying(HttpStatusCode.Unauthorized to """{"error":{"code":401}}""")

        val failure = assertIs<DriveException>(api.listSettingsFiles("f", null).exceptionOrNull())
        assertIs<DriveFailure.SignInExpired>(failure.failure)
    }

    /** Everything else may be temporary, and must not send the user back through a sign in. */
    @Test
    fun `a server error is temporary and not a dead sign in`() = runTest {
        val api = apiReplying(HttpStatusCode.ServiceUnavailable to "")

        val failure = assertIs<DriveException>(api.listSettingsFiles("f", null).exceptionOrNull())
        assertIs<DriveFailure.Failed>(failure.failure)
    }

    /** No token, no call: the failure comes back as it is rather than as a Drive error. */
    @Test
    fun `a missing sign in fails before anything is sent`() = runTest {
        val api = apiReplying(
            HttpStatusCode.OK to "{}",
            token = Result.failure(TokenException(TokenFailure.NotSignedIn))
        )

        val failure = assertIs<TokenException>(api.listSettingsFiles("f", null).exceptionOrNull())
        assertIs<TokenFailure.NotSignedIn>(failure.failure)
        assertTrue(requests.isEmpty(), "nothing should have been sent")
    }

    @Test
    fun `an answer that is not json fails rather than crashing`() = runTest {
        val api = apiReplying(HttpStatusCode.OK to "<html>a proxy said no</html>")

        assertTrue(api.listSettingsFiles("f", null).isFailure)
    }

    /** Every call carries the token, or Drive answers 401 to all of them. */
    @Test
    fun `the access token is sent`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
            respond(ByteReadChannel("""{"files":[]}"""), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        GoogleDriveApi(HttpClient(engine), accessToken = { _ -> Result.success("access-1") }).listSettingsFiles("f", null).getOrThrow()
    }
}
