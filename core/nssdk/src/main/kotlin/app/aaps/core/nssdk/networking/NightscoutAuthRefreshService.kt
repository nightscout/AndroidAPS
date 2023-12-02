package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.remotemodel.RemoteAuthResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Created by adrian on 2019-01-04.
 */

internal interface NightscoutAuthRefreshService {

    @GET("/api/v2/authorization/request/{refreshToken}")
    fun refreshToken(@Path("refreshToken") refreshToken: String): Call<RemoteAuthResponse>
}
