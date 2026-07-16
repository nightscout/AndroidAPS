package app.aaps.pump.aaruy.api

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    var code:Int,
    var msg:String,
    var data:DataResponse
)

data class DataResponse(
    var id: Int,
    var index: Int,
    var sn: String
)

data class ApiCgmResponse(
    var code:Int,
    var msg:String,
    var data:DataCgmResponse
)

data class ApiLastIndexResponse(
    var code:Int,
    var msg:String,
    var data:DataLastIndexResponse
)

data class DataCgmResponse(
    var id: Int,
    var record_time: Long,
    var sn: String
)

data class DataLastIndexResponse(
    var id: Int,
    var index: Int,
    var kind: Int,
    var record_time: Long,
    var value: String
)

data class AaruyPumpLogDto(
    @SerializedName("id") val id: String,
    @SerializedName("value") val value: String,
)