package info.nightscout.interfaces.aps

data class AutosensResult(

    //default values to show when autosens algorithm is not called
    var ratio: Double = 1.0,
    var carbsAbsorbed: Double = 0.0,
    var sensResult: String = "autosens not available",
    var pastSensitivity: String = "",
    var ratioLimit: String = ""
)