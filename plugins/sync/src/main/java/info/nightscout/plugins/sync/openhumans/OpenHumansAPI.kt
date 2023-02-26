package info.nightscout.plugins.sync.openhumans

import android.annotation.SuppressLint
import android.util.Base64
import info.nightscout.plugins.sync.di.BaseUrl
import info.nightscout.plugins.sync.di.ClientId
import info.nightscout.plugins.sync.di.ClientSecret
import info.nightscout.plugins.sync.di.RedirectUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

internal class OpenHumansAPI @Inject constructor(
    @BaseUrl
    private val baseUrl: String,
    @ClientId
    clientId: String,
    @ClientSecret
    clientSecret: String,
    @RedirectUrl
    private val redirectUrl: String
) {
    private val authHeader = "Basic " + Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
    private val client = OkHttpClient()

    suspend fun exchangeBearerToken(bearerToken: String) = sendTokenRequest(FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("redirect_uri", redirectUrl)
        .add("code", bearerToken)
        .build())

    suspend fun refreshAccessToken(refreshToken: String) = sendTokenRequest(FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("redirect_uri", redirectUrl)
        .add("refresh_token", refreshToken)
        .build())

    private suspend fun sendTokenRequest(body: FormBody): OAuthTokens {
        val timestamp = System.currentTimeMillis()
        val request = Request.Builder()
            .url("$baseUrl/oauth2/token/")
            .addHeader("Authorization", authHeader)
            .post(body)
            .build()
        val response = request.await()
        val json = response.body?.let { JSONObject(it.string()) }
        if (json == null || !response.isSuccessful) throw OHHttpException(response.code, response.message, json?.getString("error"))
        val accessToken = json.getString("access_token") ?: throw OHProtocolViolationException("access_token missing")
        val refreshToken = json.getString("refresh_token") ?: throw OHProtocolViolationException("refresh_token missing")
        if (!json.has("expires_in")) throw OHProtocolViolationException("expires_in missing")
        val expiresAt = timestamp + json.getInt("expires_in") * 1000L
        return OAuthTokens(accessToken, refreshToken, expiresAt)
    }

    suspend fun getProjectMemberId(accessToken: String): String {
        val request = Request.Builder()
            .url("$baseUrl/api/direct-sharing/project/exchange-member/?access_token=$accessToken")
            .get()
            .build()
        val response = request.await()
        val json = response.body?.let { JSONObject(it.string()) }
        if (json == null || !response.isSuccessful) throw OHHttpException(response.code, response.message, json?.getString("detail"))
        return json.getString("project_member_id") ?: throw OHProtocolViolationException("project_member_id missing")
    }

    suspend fun prepareFileUpload(accessToken: String, fileName: String, metadata: FileMetadata): PreparedUpload {
        val request = Request.Builder()
            .url("$baseUrl/api/direct-sharing/project/files/upload/direct/?access_token=$accessToken")
            .post(FormBody.Builder()
                .add("filename", fileName)
                .add("metadata", metadata.toJSON().toString())
                .build())
            .build()
        val response = request.await()
        val json = response.body?.let { JSONObject(it.string()) }
        if (json == null || !response.isSuccessful) throw OHHttpException(response.code, response.message, json?.getString("detail"))
        return PreparedUpload(
            fileId = json.getString("id") ?: throw OHProtocolViolationException("id missing"),
            uploadURL = json.getString("url") ?: throw OHProtocolViolationException("url missing")
        )
    }

    suspend fun uploadFile(url: String, content: ByteArray) {
        val request = Request.Builder()
            .url(url)
            .put(object : RequestBody() {
                override fun contentType(): MediaType? = null

                override fun contentLength(): Long = content.size.toLong()

                override fun writeTo(sink: BufferedSink) {
                    sink.write(content)
                }
            })
            .build()
        val response = request.await()
        if (!response.isSuccessful) throw OHHttpException(response.code, response.message, null)
    }

    suspend fun completeFileUpload(accessToken: String, fileId: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/direct-sharing/project/files/upload/complete/?access_token=$accessToken")
            .post(FormBody.Builder()
                .add("file_id", fileId)
                .build())
            .build()
        val response = request.await()
        if (!response.isSuccessful) throw OHHttpException(response.code, response.message, null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun Request.await(): Response {
        val call = client.newCall(this)
        return suspendCancellableCoroutine {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    it.resume(response, null)
                }
            })
            it.invokeOnCancellation { call.cancel() }
        }
    }

    data class FileMetadata(
        val tags: List<String>,
        val description: String,
        val md5: String? = null,
        val creationDate: Long? = null,
        val startDate: Long? = null,
        val endDate: Long? = null
    ) {

        fun toJSON(): JSONObject {
            val jsonObject = JSONObject()
            jsonObject.put("tags", JSONArray().apply { tags.forEach { put(it) } })
            jsonObject.put("description", description)
            jsonObject.put("md5", md5)
            creationDate?.let { jsonObject.put("creation_date", iso8601DateFormatter.format(Date(it))) }
            startDate?.let { jsonObject.put("start_date", iso8601DateFormatter.format(Date(it))) }
            endDate?.let { jsonObject.put("end_date", iso8601DateFormatter.format(Date(it))) }
            return jsonObject
        }
    }

    data class PreparedUpload(
        val fileId: String,
        val uploadURL: String
    )

    data class OAuthTokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )

    data class OHHttpException(
        val code: Int,
        val meaning: String,
        val detail: String?
    ) : RuntimeException() {
        override val message: String get() = toString()
    }

    class OHProtocolViolationException(
        override val message: String
    ) : RuntimeException()

    private companion object {
        @SuppressLint("SimpleDateFormat")
        val iso8601DateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    }
}