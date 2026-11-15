package app.aaps.plugins.sync.tidepool.comm

import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

const val SESSION_TOKEN_HEADER: String = "x-tidepool-session-token"

/**
 * The Tidepool calls AAPS makes. The client headers are added to every call by [ClientHeadersInterceptor].
 * There is no call to close a dataset on purpose: a continuous dataset must stay open, and a closed one
 * cannot be written to again.
 */
interface TidepoolApiService {

    @DELETE("/v1/datasets/{dataSetId}")
    fun deleteDataSet(@Header(SESSION_TOKEN_HEADER) token: String, @Path("dataSetId") id: String): Call<DatasetReplyMessage>

    /**
     * List the user's datasets, newest first. Deleted datasets are not returned.
     * A `null` [deviceId] is left out of the query, so datasets with any device id (or none) match.
     */
    @GET("/v1/users/{userId}/data_sets")
    fun getDataSets(
        @Header(SESSION_TOKEN_HEADER) token: String,
        @Path("userId") id: String,
        @Query("client.name") clientName: String,
        @Query("deviceId") deviceId: String?,
        @Query("size") size: Int
    ): Call<List<DatasetReplyMessage>>

    @POST("/v1/users/{userId}/data_sets")
    fun openDataSet(@Header(SESSION_TOKEN_HEADER) token: String, @Path("userId") id: String, @Body body: RequestBody): Call<DatasetReplyMessage>

    @POST("/v1/datasets/{sessionId}/data")
    fun doUpload(@Header(SESSION_TOKEN_HEADER) token: String, @Path("sessionId") id: String, @Body body: RequestBody): Call<UploadReplyMessage>
}
