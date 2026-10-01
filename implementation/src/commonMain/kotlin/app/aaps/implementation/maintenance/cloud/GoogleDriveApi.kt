package app.aaps.implementation.maintenance.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive

/** One file as Drive describes it, with only the fields the settings screens read. */
data class DriveFile(val id: String, val name: String, val modifiedTime: String?)

/** A page of files, and the token that asks for the next one. Null token means this was the last. */
data class DriveFilePage(val files: List<DriveFile>, val nextPageToken: String?)

/** Why a Drive call did not work, when the difference changes what the user should be told. */
sealed interface DriveFailure {

    /**
     * The sign in is finished - revoked, or the password changed. Signing in again fixes it.
     *
     * Reached only by a 401 that survived a forced token refresh. The caller throws the stored
     * credentials away on this, so widening what produces it signs users out: 403 must not, because
     * Google uses it for quota and permission rather than for expiry.
     */
    data object SignInExpired : DriveFailure

    /** The network, or Google, or us - a full Drive and a rate limit land here. Trying again later may work. */
    data class Failed(val message: String) : DriveFailure
}

/**
 * The Google Drive calls the settings export needs, over the REST API.
 *
 * There is no Google SDK here on purpose - and no need for one. Drive is an HTTP API, and the
 * Android side already spoke it directly with OkHttp rather than through Play Services, which is the
 * single fact that makes this portable at all. Ktor gives the same calls an engine on every
 * platform: `NSURLSession` on iOS, OkHttp on Android.
 *
 * ## Scope
 *
 * `drive.file`, which grants access only to files this app created. AAPS cannot read the rest of
 * someone's Drive, and asking for more would be asking for permission over documents that have
 * nothing to do with it.
 *
 * ## Failures are told apart, and 403 is not one of them
 *
 * A 401 that survives a forced token refresh means the sign in is finished and the user has to do
 * something; anything else may be temporary and should be tried again. Collapsing the two produces
 * either a screen that nags a user whose connection dropped, or one that silently retries a sign in
 * that will never work again.
 *
 * **403 is deliberately not treated as expiry.** Google's error guide is explicit that 401 is
 * invalid credentials while 403 is permission and quota - `rateLimitExceeded`,
 * `userRateLimitExceeded`, `storageQuotaExceeded`, `insufficientFilePermissions` - and that quota
 * errors want a retry rather than a new sign in. The Android `GoogleDriveManager` this replaced did
 * treat 403 as a dead sign in, so a user whose Drive was full, or who hit a rate limit, was signed
 * out of their own backup.
 *
 * Note this differs from `NsAuth` in `:core:nssdk`, which *does* refresh on 403. That is not an
 * inconsistency: Nightscout answers 403 for an expired token where Google does not, and NsAuth
 * refreshes rather than clearing anything. Read each against the server it talks to.
 *
 * See https://developers.google.com/workspace/drive/api/guides/handle-errors
 */
class GoogleDriveApi(
    private val http: HttpClient,
    private val accessToken: suspend (forceRefresh: Boolean) -> Result<String>,
    private val driveUrl: String = DRIVE_API_URL,
    private val uploadUrl: String = UPLOAD_API_URL
) {

    /** Whether the sign in works and Drive answers. Cheapest call that proves both. */
    suspend fun testConnection(): Result<Boolean> = call { token ->
        http.get("$driveUrl/about?fields=user") { bearer(token) }
    }.map { true }

    /**
     * The settings exports in [folderId], newest first.
     *
     * Asks for only the four fields the list shows. Drive returns everything it knows otherwise, and
     * on a folder with a year of backups that is a large answer to parse for four values.
     */
    suspend fun listSettingsFiles(folderId: String, pageToken: String?, pageSize: Int = 10): Result<DriveFilePage> =
        call { token ->
            val query = encode("'$folderId' in parents and trashed = false and mimeType != '$FOLDER_MIME'")
            val page = pageToken?.let { "&pageToken=${encode(it)}" } ?: ""
            http.get(
                "$driveUrl/files?q=$query&fields=files(id,name,modifiedTime,mimeType),nextPageToken" +
                    "&pageSize=$pageSize&orderBy=modifiedTime desc&supportsAllDrives=true$page"
            ) { bearer(token) }
        }.mapCatching { body ->
            val json = Json.parseToJsonElement(body).jsonObject
            DriveFilePage(
                files = json["files"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toDriveFile() },
                nextPageToken = json["nextPageToken"]?.jsonPrimitive?.content
            )
        }

    /** The folders directly inside [parentId], for the folder picker. */
    suspend fun listFolders(parentId: String = "root"): Result<List<DriveFile>> = call { token ->
        val query = encode("'$parentId' in parents and trashed = false and mimeType = '$FOLDER_MIME'")
        http.get("$driveUrl/files?q=$query&fields=files(id,name,modifiedTime)&pageSize=100&supportsAllDrives=true") { bearer(token) }
    }.mapCatching { body ->
        Json.parseToJsonElement(body).jsonObject["files"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toDriveFile() }
    }

    suspend fun createFolder(name: String, parentId: String = "root"): Result<String> = call { token ->
        http.post("$driveUrl/files?fields=id&supportsAllDrives=true") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("mimeType", JsonPrimitive(FOLDER_MIME))
                    put("parents", JsonArray(listOf(JsonPrimitive(parentId))))
                }.toString()
            )
        }
    }.mapCatching { body -> Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content }

    /**
     * Uploads a file in one request, metadata and content together.
     *
     * `multipart/related`, which is Drive's own upload shape and **not** the `multipart/form-data`
     * that Ktor's `MultiPartFormDataContent` builds - so the body is assembled here rather than by
     * the client. The two look alike and are not interchangeable: form-data parts carry a
     * `Content-Disposition` and Drive ignores the upload.
     */
    suspend fun uploadFile(
        name: String,
        content: ByteArray,
        parentId: String,
        mimeType: String = "application/json"
    ): Result<String> = call { token ->
        val metadata = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("parents", JsonArray(listOf(JsonPrimitive(parentId))))
        }.toString()

        val body = buildRelatedBody(metadata, content, mimeType)
        http.post("$uploadUrl/files?uploadType=multipart&fields=id&supportsAllDrives=true") {
            bearer(token)
            header(HttpHeaders.ContentType, "multipart/related; boundary=$BOUNDARY")
            setBody(body)
        }
    }.mapCatching { body -> Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content }

    /** The bytes of a file. `alt=media` is what asks for the content rather than the description. */
    suspend fun downloadFile(fileId: String): Result<ByteArray> = callForBytes { token ->
        http.get("$driveUrl/files/${encode(fileId)}?alt=media&supportsAllDrives=true") { bearer(token) }
    }

    /**
     * The id of [path], making any part of it that is missing.
     *
     * A path rather than a single folder because the user picks something like `AAPS/Exports`, and a
     * fresh Drive has neither level yet.
     */
    suspend fun folderIdForPath(path: String, baseParentId: String = "root"): Result<String> {
        var parent = baseParentId
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            val existing = listFolders(parent).getOrElse { return Result.failure(it) }.firstOrNull { it.name == segment }
            parent = existing?.id ?: createFolder(segment, parent).getOrElse { return Result.failure(it) }
        }
        return Result.success(parent)
    }

    /** Runs a call with a fresh token and gives back the answer as text. */
    private suspend fun call(request: suspend (String) -> HttpResponse): Result<String> =
        retrying(request) { it.bodyAsText() }

    /** The same, for the one call that wants the file itself rather than a description of it. */
    private suspend fun callForBytes(request: suspend (String) -> HttpResponse): Result<ByteArray> =
        retrying(request) { it.bodyAsBytes() }

    /**
     * Runs a call, and runs it a second time on a 401 with a forced refresh first.
     *
     * Whether the stored access token is still good is worked out from this device's clock, so a
     * phone a few minutes fast - or a token Google dropped early - sends one Drive believes is
     * expired while we believe it is not. That arrives as a 401 and looks exactly like a sign in the
     * user revoked, and the caller answers a revoked sign in by throwing the refresh token away. One
     * forced refresh tells the two apart: if a token minted seconds ago is also refused, the second
     * 401 is the honest one and is passed on.
     */
    private suspend fun <T> retrying(
        request: suspend (String) -> HttpResponse,
        read: suspend (HttpResponse) -> T
    ): Result<T> {
        val first = attempt(request, read, forceRefresh = false)
        val failure = (first.exceptionOrNull() as? DriveException)?.failure
        if (failure !is DriveFailure.SignInExpired) return first
        return attempt(request, read, forceRefresh = true)
    }

    /** One try. Anything but success becomes a [DriveFailure]. */
    private suspend fun <T> attempt(
        request: suspend (String) -> HttpResponse,
        read: suspend (HttpResponse) -> T,
        forceRefresh: Boolean
    ): Result<T> {
        val token = accessToken(forceRefresh).getOrElse { return Result.failure(it) }
        return runCatching {
            val response = request(token)
            if (!response.status.isSuccess()) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw DriveException(failureFor(response, body))
            }
            read(response)
        }
    }

    /**
     * 401 and nothing else.
     *
     * Adding 403 here would be the obvious-looking change and is wrong - see the class KDoc. Every
     * other status, 403 included, becomes [DriveFailure.Failed], which the caller keeps the sign in
     * through.
     */
    private fun failureFor(response: HttpResponse, body: String): DriveFailure =
        if (response.status.value == UNAUTHORIZED) DriveFailure.SignInExpired
        else DriveFailure.Failed("Drive refused the request (${response.status.value})${body.take(200).ifEmpty { "" }}")

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun JsonObject.toDriveFile(): DriveFile? {
        val id = this["id"]?.jsonPrimitive?.content ?: return null
        val name = this["name"]?.jsonPrimitive?.content ?: return null
        return DriveFile(id, name, this["modifiedTime"]?.jsonPrimitive?.content)
    }

    /** `--boundary`, the metadata part, the content part, then the closing boundary. */
    private fun buildRelatedBody(metadata: String, content: ByteArray, mimeType: String): ByteArray {
        val head = (
            "--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$BOUNDARY\r\nContent-Type: $mimeType\r\n\r\n"
            ).encodeToByteArray()
        val tail = "\r\n--$BOUNDARY--".encodeToByteArray()
        return head + content + tail
    }

    /** Percent encoding for a query value; a Drive query is full of spaces and quotes. */
    private fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '.' || char == '_' || char == '~') {
                append(char)
            } else {
                append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
            }
        }
    }

    companion object {

        const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_API_URL = "https://www.googleapis.com/upload/drive/v3"

        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val BOUNDARY = "aaps-boundary-8f2a1c"
        private const val UNAUTHORIZED = 401
        private const val HEX = "0123456789ABCDEF"
    }
}

/** Carries a [DriveFailure] through a [Result], so the reason survives as far as the user. */
class DriveException(val failure: DriveFailure) : Exception(failure.toString())
