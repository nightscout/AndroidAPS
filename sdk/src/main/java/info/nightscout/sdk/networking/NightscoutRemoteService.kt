package info.nightscout.sdk.networking

import com.google.gson.JsonElement
import info.nightscout.sdk.remotemodel.NSResponse
import info.nightscout.sdk.remotemodel.RemoteEntry
import info.nightscout.sdk.remotemodel.RemoteStatusResponse
import retrofit2.Response
import retrofit2.http.GET

/**
 * Created by adrian on 2019-12-23.
 */

internal interface NightscoutRemoteService {

    @GET("v3/status")
    // used to get the raw response for more error checking. E.g. to give the user better feedback after new settings.
    suspend fun statusVerbose(): Response<NSResponse<RemoteStatusResponse>>

    @GET("v3/status")
    suspend fun statusSimple(): NSResponse<RemoteStatusResponse>

    @GET("v3/entries")
    suspend fun getEntries(): List<JsonElement>

    @GET("v3/entries?sort\$desc=date&type=sgv")
    suspend fun getSgvs(): Response<NSResponse<List<RemoteEntry>>>
}
