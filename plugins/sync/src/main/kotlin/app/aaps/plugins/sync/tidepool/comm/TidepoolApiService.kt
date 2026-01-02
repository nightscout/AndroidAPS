package app.aaps.plugins.sync.tidepool.comm

import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

const val SESSION_TOKEN_HEADER: String = "x-tidepool-session-token"

interface TidepoolApiService {

    @Headers(
        "User-Agent: AAPS- " + "1.0",
        "X-Tidepool-Client-Name: aaps",
        "X-Tidepool-Client-Version: 0.2.0"
    )

    @Suppress("unused")
    @DELETE("/v1/datasets/{dataSetId}")
    fun deleteDataSet(@Header(SESSION_TOKEN_HEADER) token: String, @Path("dataSetId") id: String): Call<DatasetReplyMessage>

    @GET("/v1/users/{userId}/data_sets")
    fun getOpenDataSets(
        @Header(SESSION_TOKEN_HEADER) token: String,
        @Path("userId") id: String,
        @Query("client.name") clientName: String,
        @Query("size") size: Int
    ): Call<List<DatasetReplyMessage>>

    @Suppress("unused")
    @GET("/v1/datasets/{dataSetId}")
    fun getDataSet(@Header(SESSION_TOKEN_HEADER) token: String, @Path("dataSetId") id: String): Call<DatasetReplyMessage>

    @POST("/v1/users/{userId}/data_sets")
    fun openDataSet(@Header(SESSION_TOKEN_HEADER) token: String, @Path("userId") id: String, @Body body: RequestBody): Call<DatasetReplyMessage>

    @POST("/v1/datasets/{sessionId}/data")
    fun doUpload(@Header(SESSION_TOKEN_HEADER) token: String, @Path("sessionId") id: String, @Body body: RequestBody): Call<UploadReplyMessage>

    @Suppress("unused")
    @PUT("/v1/datasets/{sessionId}")
    fun closeDataSet(@Header(SESSION_TOKEN_HEADER) token: String, @Path("sessionId") id: String, @Body body: RequestBody): Call<DatasetReplyMessage>

}