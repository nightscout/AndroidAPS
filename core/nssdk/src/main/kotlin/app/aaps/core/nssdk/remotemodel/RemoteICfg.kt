package app.aaps.core.nssdk.remotemodel

import com.google.gson.annotations.SerializedName

data class RemoteICfg(
    @SerializedName("insulinLabel") val insulinLabel: String,
    @SerializedName("insulinEndTime") val insulinEndTime: Long,
    @SerializedName("insulinPeakTime") val insulinPeakTime: Long,
    @SerializedName("concentration") val concentration: Double
)