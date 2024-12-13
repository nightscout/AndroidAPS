package app.aaps.pump.diaconn.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

@Suppress("LocalVariableName", "SpellCheckingInspection")
interface DiaconnApiService {

    @Headers("api-key: ${DiaconnLogUploader.UPLOAD_API_KEY}")
    @GET("v1/pumplog/last_no")
    fun getPumpLastNo(
        @Query("pump_uid") pump_uid: String,
        @Query("pump_version") pump_version: String,
        @Query("incarnation_num") incarnation_num: Int
    ): Call<LastNoResponse>

    @Headers("api-key: ${DiaconnLogUploader.UPLOAD_API_KEY}")
    @POST("v1/pumplog/save")
    fun uploadPumpLogs(@Body pumpLogDto: PumpLogDto): Call<ApiResponse>

}