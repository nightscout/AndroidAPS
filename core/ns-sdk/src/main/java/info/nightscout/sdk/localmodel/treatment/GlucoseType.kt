package info.nightscout.sdk.localmodel.treatment

import com.google.gson.annotations.SerializedName

enum class GlucoseType {
    @SerializedName("Sensor") Sensor,
    @SerializedName("Finger") Finger,
    @SerializedName("Manual") Manual
}
