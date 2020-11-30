package info.nightscout.androidaps.plugins.general.openhumans

import android.annotation.SuppressLint
import android.util.Base64
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.Disposables
import okhttp3.*
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class OpenHumansAPI(
    private val baseUrl: String,
    clientId: String,
    clientSecret: String,
    private val redirectUri: String
) {

    private val authHeader = "Basic " + Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
    private val client = OkHttpClient()

    fun exchangeAuthToken(code: String): Single<OAuthTokens> = sendTokenRequest(FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("redirect_uri", redirectUri)
        .add("code", code)
        .build())

    fun refreshAccessToken(refreshToken: String): Single<OAuthTokens> = sendTokenRequest(FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("redirect_uri", redirectUri)
        .add("refresh_token", refreshToken)
        .build())

    private fun sendTokenRequest(body: FormBody) = Request.Builder()
        .url("$baseUrl/oauth2/token/")
        .addHeader("Authorization", authHeader)
        .post(body)
        .build()
        .toSingle()
        .map { response ->
            response.use { _ ->
                val responseBody = response.body
                val jsonObject = responseBody?.let { JSONObject(it.string()) }
                if (!response.isSuccessful) throw OHHttpException(response.code, response.message, jsonObject?.getString("error"))
                if (jsonObject == null) throw OHHttpException(response.code, response.message, "No body")
                if (!jsonObject.has("expires_in")) throw OHMissingFieldException("expires_in")
                OAuthTokens(
                    accessToken = jsonObject.getString("access_token")
                        ?: throw OHMissingFieldException("access_token"),
                    refreshToken = jsonObject.getString("refresh_token")
                        ?: throw OHMissingFieldException("refresh_token"),
                    expiresAt = response.sentRequestAtMillis + jsonObject.getInt("expires_in") * 1000L
                )
            }
        }

    fun getProjectMemberId(accessToken: String): Single<String> = Request.Builder()
        .url("$baseUrl/api/direct-sharing/project/exchange-member/?access_token=$accessToken")
        .get()
        .build()
        .toSingle()
        .map {
            it.jsonBody.getString("project_member_id")
                ?: throw OHMissingFieldException("project_member_id")
        }

    fun prepareFileUpload(accessToken: String, fileName: String, metadata: FileMetadata): Single<PreparedUpload> = Request.Builder()
        .url("$baseUrl/api/direct-sharing/project/files/upload/direct/?access_token=$accessToken")
        .post(FormBody.Builder()
            .add("filename", fileName)
            .add("metadata", metadata.toJSON().toString())
            .build())
        .build()
        .toSingle()
        .map {
            val json = it.jsonBody
            PreparedUpload(
                fileId = json.getString("id") ?: throw OHMissingFieldException("id"),
                uploadURL = json.getString("url") ?: throw OHMissingFieldException("url")
            )
        }

    fun uploadFile(url: String, content: ByteArray): Completable = Request.Builder()
        .url(url)
        .put(object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun contentLength() = content.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                sink.write(content)
            }
        })
        .build()
        .toSingle()
        .doOnSuccess { response ->
            response.use { _ ->
                if (!response.isSuccessful) throw OHHttpException(response.code, response.message, null)
            }
        }
        .ignoreElement()

    fun completeFileUpload(accessToken: String, fileId: String): Completable = Request.Builder()
        .url("$baseUrl/api/direct-sharing/project/files/upload/complete/?access_token=$accessToken")
        .post(FormBody.Builder()
            .add("file_id", fileId)
            .build())
        .build()
        .toSingle()
        .doOnSuccess { it.jsonBody }
        .ignoreElement()

    private fun Request.toSingle() = Single.create<Response> {
        val call = client.newCall(this)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                it.tryOnError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                it.onSuccess(response)
            }
        })
        it.setDisposable(Disposables.fromRunnable { call.cancel() })
    }

    private val Response.jsonBody
        get() = use { _ ->
            val jsonObject = body?.let { JSONObject(it.string()) }
                ?: throw OHHttpException(code, message, null)
            if (!isSuccessful) throw OHHttpException(code, message, jsonObject.getString("detail"))
            jsonObject
        }

    data class OAuthTokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )

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

    data class OHHttpException(
        val code: Int,
        val meaning: String,
        val detail: String?
    ) : RuntimeException() {

        override val message: String get() = toString()
    }

    data class OHMissingFieldException(
        val name: String
    ) : RuntimeException() {

        override val message: String get() = toString()
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        private val iso8601DateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    }
}