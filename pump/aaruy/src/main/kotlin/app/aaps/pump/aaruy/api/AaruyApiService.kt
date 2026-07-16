package app.aaps.pump.aaruy.api

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AaruyApiService {
    @FormUrlEncoded
    @POST("open/aaps/ruypump/notice")
    fun uploadPumpLogs(
        @Field("id") friend_id: String,
        @Field("value") days: String,
        ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("open/aaps/ruypump/notice")
    fun uploadPumpHistory(
        @Field("index") index: String,
        @Field("product_code") product_code: String,
        @Field("sn") sn: String,
        @Field("record_time") record_time: String,
        @Field("kind") kind: String,    // 0:基础率 1:临时基础率 2:大剂量（立即量） 3:大剂量（延长量）\n108:进食碳水化合物 109:血糖值 110:换泵\n203:报警 204:推杆回退 205:推杆定位 206:恢复出厂设置 207：暂停
        @Field("value") value: String,
        @Field("seconds") seconds: String,
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("/open/aaps/cgm/notice")
    fun uploadCGMsHistory(
        @Field("ruypump_sn") ruypump_sn: String,
        @Field("brand") brand: String,
        @Field("product_code") product_code: String,
        @Field("sn") sn: String,
        @Field("record_time") record_time: String,
        @Field("value") value: String,
    ): Call<ApiCgmResponse>

    @FormUrlEncoded
    @POST("/open/aaps/ruypump/record/last")
    fun getLastUploadIndex(
        @Field("sn") sn: String,
    ): Call<ApiLastIndexResponse>
}