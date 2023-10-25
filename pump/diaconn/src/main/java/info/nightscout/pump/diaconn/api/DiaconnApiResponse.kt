package info.nightscout.pump.diaconn.api

import com.google.gson.annotations.SerializedName

data class LastNoResponse(val ok: Boolean, val info:Info  )

data class Info(val pumplog_no: Long)

data class ApiResponse(val ok: Boolean)

data class PumpLogDto(
    @SerializedName("app_uid") val app_uid: String,
    @SerializedName("app_version") val app_version: String,
    @SerializedName("pump_uid") val pump_uid: String,
    @SerializedName("pump_version") val pump_version: String,
    @SerializedName("incarnation_num") val incarnation_num: Int,
    @SerializedName("pumplog_info") val pumplog_info: List<PumpLog>
)

data class PumpLog(
    @SerializedName("pumplog_no") val pumplog_no: Long,
    @SerializedName("pumplog_wrapping_count") val pumplog_wrapping_count: Int,
    @SerializedName("pumplog_data") val pumplog_data: String,
    @SerializedName("act_type") val act_type: String
)